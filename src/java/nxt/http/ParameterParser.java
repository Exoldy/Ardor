/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2020 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.http;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.HoldingType;
import nxt.ae.Asset;
import nxt.aliases.AliasHome;
import nxt.blockchain.Appendix;
import nxt.blockchain.Bundler;
import nxt.blockchain.Chain;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.blockchain.chaincontrol.PermissionType;
import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.crypto.SecretSharingGenerator;
import nxt.crypto.SerializedMasterPublicKey;
import nxt.dgs.DigitalGoodsHome;
import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.messaging.UnencryptedEncryptToSelfMessageAppendix;
import nxt.messaging.UnencryptedEncryptedMessageAppendix;
import nxt.messaging.UnencryptedPrunableEncryptedMessageAppendix;
import nxt.ms.Currency;
import nxt.ms.ExchangeOfferHome;
import nxt.peer.FeeRateCalculator.TransactionPriority;
import nxt.shuffling.ShufflingHome;
import nxt.taggeddata.TaggedDataAttachment;
import nxt.util.Bip32Path;
import nxt.util.BooleanExpression;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;
import nxt.util.Search;
import nxt.voting.PhasingParams;
import nxt.voting.PollHome;
import nxt.voting.VoteWeighting;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nxt.http.JSONResponses.INCORRECT_ACCOUNT;
import static nxt.http.JSONResponses.INCORRECT_ALIAS;
import static nxt.http.JSONResponses.INCORRECT_ARBITRARY_MESSAGE;
import static nxt.http.JSONResponses.INCORRECT_DATA_TOO_LONG;
import static nxt.http.JSONResponses.INCORRECT_DATA_ZERO_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_FILE;
import static nxt.http.JSONResponses.INCORRECT_MESSAGE_TO_ENCRYPT;
import static nxt.http.JSONResponses.INCORRECT_PURCHASE;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_CHANNEL;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_DESCRIPTION;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_FILE;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_FILENAME;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_NAME;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_TAGS;
import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_TYPE;
import static nxt.http.JSONResponses.INCORRECT_WHITELIST;
import static nxt.http.JSONResponses.MISSING_ACCOUNT;
import static nxt.http.JSONResponses.MISSING_ALIAS_OR_ALIAS_NAME;
import static nxt.http.JSONResponses.MISSING_CHAIN;
import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.MISSING_PERMISSION;
import static nxt.http.JSONResponses.MISSING_PROPERTY;
import static nxt.http.JSONResponses.MISSING_RECIPIENT_PUBLIC_KEY;
import static nxt.http.JSONResponses.MISSING_SECRET_PHRASE_OR_PRIVATE_KEY;
import static nxt.http.JSONResponses.MISSING_TRANSACTION_BYTES_OR_JSON;
import static nxt.http.JSONResponses.UNKNOWN_ACCOUNT;
import static nxt.http.JSONResponses.UNKNOWN_ALIAS;
import static nxt.http.JSONResponses.UNKNOWN_ASSET;
import static nxt.http.JSONResponses.UNKNOWN_CHAIN;
import static nxt.http.JSONResponses.UNKNOWN_CURRENCY;
import static nxt.http.JSONResponses.UNKNOWN_GOODS;
import static nxt.http.JSONResponses.UNKNOWN_OFFER;
import static nxt.http.JSONResponses.UNKNOWN_PERMISSION;
import static nxt.http.JSONResponses.UNKNOWN_POLL;
import static nxt.http.JSONResponses.UNKNOWN_SHUFFLING;
import static nxt.http.JSONResponses.either;
import static nxt.http.JSONResponses.incorrect;
import static nxt.http.JSONResponses.missing;

public final class ParameterParser {

    public static byte getByte(HttpServletRequest req, String name, byte min, byte max, boolean isMandatory) throws ParameterException {
        return getByte(req, name, min, max, (byte) 0, isMandatory);
    }

    public static byte getByte(HttpServletRequest req, String name, byte min, byte max, byte defaultValue, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return defaultValue;
        }
        try {
            byte value = Byte.parseByte(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static int getInt(HttpServletRequest req, String name, int min, int max, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        return getInt(name, paramValue, min, max);
    }

    public static int getInt(HttpServletRequest req, String name, int min, int max, int defaultValue) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return defaultValue;
        }
        return getInt(name, paramValue, min, max);
    }

    private static int getInt(String paramName, String paramValue, int min, int max) throws ParameterException {
        try {
            int value = Integer.parseInt(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(paramName, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(paramName, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getLong(HttpServletRequest req, String name, long min, long max,
                        boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        return getLong(name, paramValue, min, max);
    }

    public static long getLong(HttpServletRequest req, String name, long min, long max,
                               long defaultValue) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return defaultValue;
        }
        return getLong(name, paramValue, min, max);
    }

    private static long getLong(String paramName, String paramValue, long min, long max) throws ParameterException {
        try {
            long value = Long.parseLong(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(paramName, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(paramName, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getUnsignedLong(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseUnsignedLong(paramValue);
            if (value == 0) { // 0 is not allowed as an id
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static long[] getUnsignedLongs(HttpServletRequest req, String name) throws ParameterException {
        String[] paramValues = req.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            throw new ParameterException(missing(name));
        }
        long[] values = new long[paramValues.length];
        try {
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] == null || paramValues[i].isEmpty()) {
                    throw new ParameterException(incorrect(name));
                }
                values[i] = Long.parseUnsignedLong(paramValues[i]);
                if (values[i] == 0) {
                    throw new ParameterException(incorrect(name));
                }
            }
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
        return values;
    }

    public static BigInteger getBigInteger(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return BigInteger.ZERO;
        }
        return new BigInteger(paramValue);
    }

    public static byte[] getBytes(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return Convert.EMPTY_BYTE;
        }
        return Convert.parseHexString(paramValue);
    }

    public static SerializedMasterPublicKey getSerializedMasterPublicKey(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        byte[] bytes = getBytes(req, name, isMandatory);
        if (bytes.length == 0 && !isMandatory) {
            return null;
        }
        try {
            return new SerializedMasterPublicKey(bytes);
        } catch (Exception e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static JSONObject getJson(HttpServletRequest req, String name) {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return null;
        }
        return (JSONObject)JSONValue.parse(paramValue);
    }

    public static JSONArray getJsonArray(HttpServletRequest req, String name) {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return null;
        }
        return (JSONArray)JSONValue.parse(paramValue);
    }

    public static String getParameter(HttpServletRequest req, String name) throws ParameterException {
        String value = Convert.emptyToNull(req.getParameter(name));
        if (value == null) {
            throw new ParameterException(missing(name));
        }
        return value;
    }

    public static long getAccountId(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return getAccountId(req, "account", isMandatory);
    }

    public static long getAccountId(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseAccountId(paramValue);
            if (value == 0) {
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static long[] getAccountIds(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String[] paramValues = req.getParameterValues("account");
        if (paramValues == null || paramValues.length == 0) {
            if (isMandatory) {
                throw new ParameterException(MISSING_ACCOUNT);
            } else {
                return Convert.EMPTY_LONG;
            }
        }
        long[] values = new long[paramValues.length];
        try {
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] == null || paramValues[i].isEmpty()) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
                values[i] = Convert.parseAccountId(paramValues[i]);
                if (values[i] == 0) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
            }
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ACCOUNT);
        }
        return values;
    }

    public static AliasHome.Alias getAlias(HttpServletRequest req) throws ParameterException {
        long aliasId;
        try {
            aliasId = Convert.parseUnsignedLong(Convert.emptyToNull(req.getParameter("alias")));
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ALIAS);
        }
        String aliasName = Convert.emptyToNull(req.getParameter("aliasName"));
        ChildChain childChain = getChildChain(req);
        AliasHome.Alias alias;
        if (aliasId != 0) {
            alias = childChain.getAliasHome().getAlias(aliasId);
        } else if (aliasName != null) {
            alias = childChain.getAliasHome().getAlias(aliasName);
        } else {
            throw new ParameterException(MISSING_ALIAS_OR_ALIAS_NAME);
        }
        if (alias == null) {
            throw new ParameterException(UNKNOWN_ALIAS);
        }
        return alias;
    }

    public static long getAmountNQT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "amountNQT", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getAmountNQTPerShare(HttpServletRequest req) throws ParameterException {
        return getLong(req, "amountNQTPerShare", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getPriceNQT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "priceNQT", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getPriceNQTPerShare(HttpServletRequest req) throws ParameterException {
        return getLong(req, "priceNQTPerShare", 1L, Constants.MAX_BALANCE_NQT, true);
    }
    public static long getPriceNQTPerCoin(HttpServletRequest req) throws ParameterException {
        return getLong(req, "priceNQTPerCoin", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static long getRateNQTPerUnit(HttpServletRequest req) throws ParameterException {
        return getLong(req, "rateNQTPerUnit", 1L, Constants.MAX_BALANCE_NQT, true);
    }

    public static PollHome.Poll getPoll(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = getChildChain(req);
        PollHome.Poll poll = childChain.getPollHome().getPoll(getUnsignedLong(req, "poll", true));
        if (poll == null) {
            throw new ParameterException(UNKNOWN_POLL);
        }
        return poll;
    }

    public static Asset getAsset(HttpServletRequest req) throws ParameterException {
        Asset asset = Asset.getAsset(getUnsignedLong(req, "asset", true));
        if (asset == null) {
            throw new ParameterException(UNKNOWN_ASSET);
        }
        return asset;
    }

    public static Currency getCurrency(HttpServletRequest req) throws ParameterException {
        return getCurrency(req, true);
    }

    public static Currency getCurrency(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        Currency currency = Currency.getCurrency(getUnsignedLong(req, "currency", isMandatory));
        if (isMandatory && currency == null) {
            throw new ParameterException(UNKNOWN_CURRENCY);
        }
        return currency;
    }

    public static ExchangeOfferHome.BuyOffer getBuyOffer(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = ParameterParser.getChildChain(req);
        ExchangeOfferHome.BuyOffer offer = childChain.getExchangeOfferHome().getBuyOffer(getUnsignedLong(req, "offer", true));
        if (offer == null) {
            throw new ParameterException(UNKNOWN_OFFER);
        }
        return offer;
    }

    public static ExchangeOfferHome.SellOffer getSellOffer(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = ParameterParser.getChildChain(req);
        ExchangeOfferHome.SellOffer offer = childChain.getExchangeOfferHome().getSellOffer(getUnsignedLong(req, "offer", true));
        if (offer == null) {
            throw new ParameterException(UNKNOWN_OFFER);
        }
        return offer;
    }

    public static ShufflingHome.Shuffling getShuffling(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = ParameterParser.getChildChain(req);
        ShufflingHome.Shuffling shuffling = childChain.getShufflingHome().getShuffling(getBytes(req, "shufflingFullHash", true));
        if (shuffling == null) {
            throw new ParameterException(UNKNOWN_SHUFFLING);
        }
        return shuffling;
    }

    public static long getQuantityQNT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "quantityQNT", 1L, Constants.MAX_ASSET_QUANTITY_QNT, true);
    }

    public static long getUnitsQNT(HttpServletRequest req) throws ParameterException {
        return getLong(req, "unitsQNT", 1L, Constants.MAX_CURRENCY_TOTAL_SUPPLY, true);
    }

    public static DigitalGoodsHome.Goods getGoods(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = getChildChain(req);
        DigitalGoodsHome.Goods goods = childChain.getDigitalGoodsHome().getGoods(getUnsignedLong(req, "goods", true));
        if (goods == null) {
            throw new ParameterException(UNKNOWN_GOODS);
        }
        return goods;
    }

    public static int getGoodsQuantity(HttpServletRequest req) throws ParameterException {
        return getInt(req, "quantity", 0, Constants.MAX_DGS_LISTING_QUANTITY, true);
    }

    public static int[] getBip32Path(HttpServletRequest req, String name) {
        String pathStr = Convert.emptyToNull(req.getParameter(name));
        if (pathStr == null) {
            return Constants.getBip32RootPath().toPathArray();
        }
        return Bip32Path.bip32StrToPath(pathStr);
    }

    public static EncryptedData getEncryptedData(HttpServletRequest req, String messageType) throws ParameterException {
        String dataString = Convert.emptyToNull(req.getParameter(messageType + "Data"));
        String nonceString = Convert.emptyToNull(req.getParameter(messageType + "Nonce"));
        if (nonceString == null) {
            return null;
        }
        byte[] data;
        byte[] nonce;
        try {
            nonce = Convert.parseHexString(nonceString);
        } catch (RuntimeException e) {
            throw new ParameterException(JSONResponses.incorrect(messageType + "Nonce"));
        }
        if (dataString != null) {
            try {
                data = Convert.parseHexString(dataString);
            } catch (RuntimeException e) {
                throw new ParameterException(JSONResponses.incorrect(messageType + "Data"));
            }
        } else {
            FileData fileData = getFileData(req, messageType + "File",false);
            if (fileData == null) {
                return null;
            }
            data = fileData.getData();
        }
        return new EncryptedData(data, nonce);
    }

    public static EncryptToSelfMessageAppendix getEncryptToSelfMessage(HttpServletRequest req) throws ParameterException {
        boolean isText = !"false".equalsIgnoreCase(req.getParameter("messageToEncryptToSelfIsText"));
        boolean compress = !"false".equalsIgnoreCase(req.getParameter("compressMessageToEncryptToSelf"));
        byte[] plainMessageBytes = null;
        EncryptedData encryptedData = ParameterParser.getEncryptedData(req, "encryptToSelfMessage");
        if (encryptedData == null) {
            String plainMessage = Convert.emptyToNull(req.getParameter("messageToEncryptToSelf"));
            if (plainMessage == null) {
                return null;
            }
            try {
                plainMessageBytes = isText ? Convert.toBytes(plainMessage) : Convert.parseHexString(plainMessage);
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_MESSAGE_TO_ENCRYPT);
            }
            byte[] privateKey = getPrivateKey(req, false);
            if (privateKey != null) {
                byte[] publicKey = Crypto.getPublicKey(privateKey);
                encryptedData = Account.encryptTo(privateKey, publicKey, plainMessageBytes, compress);
            }
        }
        if (encryptedData != null) {
            return new EncryptToSelfMessageAppendix(encryptedData, isText, compress);
        } else {
            return new UnencryptedEncryptToSelfMessageAppendix(plainMessageBytes, isText, compress);
        }
    }

    public static DigitalGoodsHome.Purchase getPurchase(HttpServletRequest req) throws ParameterException {
        ChildChain childChain = getChildChain(req);
        DigitalGoodsHome.Purchase purchase = childChain.getDigitalGoodsHome().getPurchase(getUnsignedLong(req, "purchase", true));
        if (purchase == null) {
            throw new ParameterException(INCORRECT_PURCHASE);
        }
        return purchase;
    }

    public static byte[] getPrivateKey(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return getPrivateKey(req, null, isMandatory);
    }

    @SuppressWarnings("DuplicatedCode")
    public static byte[] getPrivateKey(HttpServletRequest req, String prefix, boolean isMandatory) throws ParameterException {
        String privateKeyParam = prefix == null ? "privateKey" : (prefix + "PrivateKey");
        byte[] privateKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter(privateKeyParam)));
        if (privateKey != null) {
            return privateKey;
        }
        String[] sharedPieces = req.getParameterValues("sharedPiece");
        if (sharedPieces != null && SecretSharingGenerator.isPrivateKeySecret(sharedPieces)) {
            List<String> clientSharedPieces = Arrays.asList(sharedPieces);
            long accountId = getAccountId(req, "sharedPieceAccount", false);
            String sharedSecretAccount = Convert.rsAccount(accountId);
            List<String> serverSharedPieces = Nxt.getStringListProperty("nxt.privateKeyPieces." + sharedSecretAccount);
            List<String> allSharedPieces = Stream.concat(clientSharedPieces.stream(), serverSharedPieces.stream()).distinct().collect(Collectors.toList());
            privateKey = SecretSharingGenerator.combinePrivateKey(allSharedPieces.toArray(new String[]{}));
            if (accountId != 0 && Account.getId(Crypto.getPublicKey(privateKey)) != accountId) {
                throw new ParameterException(JSONResponses.error(String.format("Combined secret does not reproduce private key for account %s", sharedSecretAccount)));
            }
            return privateKey;
        }
        String secretPhrase = getSecretPhrase(req, prefix, isMandatory);
        if (secretPhrase != null) {
            return Crypto.getPrivateKey(secretPhrase);
        }
        if (isMandatory) {
            throw new ParameterException(MISSING_SECRET_PHRASE_OR_PRIVATE_KEY);
        } else {
            return null;
        }
    }

    /**
     * @param req {@link HttpServletRequest}
     * @param isMandatory true if the secret phrase is mandatory
     * @return The extracted from the request secret phrase
     * @deprecated use getPrivateKey instead.
     * @throws ParameterException If missing or other error
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static String getSecretPhrase(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return getSecretPhrase(req, null, isMandatory);
    }

    @SuppressWarnings("DuplicatedCode")
    private static String getSecretPhrase(HttpServletRequest req, String prefix, boolean isMandatory) throws ParameterException {
        String secretPhraseParam = prefix == null ? "secretPhrase" : (prefix + "SecretPhrase");
        String secretPhrase = Convert.emptyToNull(req.getParameter(secretPhraseParam));
        if (secretPhrase != null) {
            return secretPhrase;
        }
        String[] sharedPieces = req.getParameterValues("sharedPiece");
        if (sharedPieces == null || prefix != null) {
            if (isMandatory) {
                throw new ParameterException(MISSING_SECRET_PHRASE_OR_PRIVATE_KEY);
            } else {
                return null;
            }
        }
        if (SecretSharingGenerator.isPrivateKeySecret(sharedPieces)) {
            throw new ParameterException(JSONResponses.error("Combined secret phrase is of type PRIVATE_KEY, use getPrivateKey()"));
        }
        List<String> clientSharedPieces = Arrays.asList(sharedPieces);
        long accountId = getAccountId(req, "sharedPieceAccount", false);
        String sharedSecretAccount = Convert.rsAccount(accountId);
        List<String> serverSharedPieces = Nxt.getStringListProperty("nxt.secretPhrasePieces." + sharedSecretAccount);
        List<String> allSharedPieces = Stream.concat(clientSharedPieces.stream(), serverSharedPieces.stream()).distinct().collect(Collectors.toList());
        secretPhrase = SecretSharingGenerator.combine(allSharedPieces.toArray(new String[]{}));
        if (accountId != 0 && Account.getId(Crypto.getPublicKey(Crypto.getPrivateKey(secretPhrase))) != accountId) {
            throw new ParameterException(JSONResponses.error(String.format("Combined secret phrase does not reproduce secret phrase for account %s", sharedSecretAccount)));
        }
        return secretPhrase;
    }

    public static byte[] getPublicKey(HttpServletRequest req) throws ParameterException {
        return getPublicKey(req, null);
    }

    public static byte[] getPublicKey(HttpServletRequest req, String prefix) throws ParameterException {
        String publicKeyParam = prefix == null ? "publicKey" : (prefix + "PublicKey");
        byte[] privateKey = getPrivateKey(req, prefix, false);
        boolean isVoucher = "true".equalsIgnoreCase(req.getParameter("voucher"));
        if (privateKey == null || isVoucher) {
            try {
                byte[] publicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter(publicKeyParam)));
                if (publicKey == null) {
                    if (isVoucher) {
                        throw new ParameterException(missing(publicKeyParam));
                    } else {
                        throw new ParameterException(missing(
                                prefix == null ? "secretPhrase" : (prefix + "SecretPhrase"),
                                prefix == null ? "privateKey" : (prefix + "privateKey"),
                                publicKeyParam));
                    }
                }
                if (!Crypto.isCanonicalPublicKey(publicKey)) {
                    throw new ParameterException(incorrect(publicKeyParam));
                }
                return publicKey;
            } catch (RuntimeException e) {
                throw new ParameterException(incorrect(publicKeyParam));
            }
        } else {
            return Crypto.getPublicKey(privateKey);
        }
    }

    public static List<byte[]> getPublicKeys(HttpServletRequest req, String name) throws ParameterException {
        String[] paramValues = req.getParameterValues(name);
        if (paramValues == null || paramValues.length == 0) {
            throw new ParameterException(missing(name));
        }
        List<byte[]> publicKeys = new ArrayList<>();
        for (String keyString : paramValues) {
            for (String key : keyString.split("\\n")) {
                byte[] publicKey = Convert.parseHexString(key.trim());
                if (publicKey.length != 0) {
                    if (!Crypto.isCanonicalPublicKey(publicKey)) {
                        throw new ParameterException(incorrect(name));
                    }
                    publicKeys.add(publicKey);
                }
            }
        }
        return publicKeys;
    }

    public static Account getSenderAccount(HttpServletRequest req) throws ParameterException {
        byte[] publicKey = getPublicKey(req);
        Account account = Account.getAccount(publicKey);
        if (account == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        return account;
    }

    public static long getSenderId(HttpServletRequest req) throws ParameterException {
        byte[] publicKey = getPublicKey(req);
        return Account.getId(publicKey);
    }

    public static Account getAccount(HttpServletRequest req) throws ParameterException {
        return getAccount(req, true);
    }

    public static Account getAccount(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        long accountId = getAccountId(req, "account", isMandatory);
        if (accountId == 0 && !isMandatory) {
            return null;
        }
        Account account = Account.getAccount(accountId);
        if (account == null) {
            throw new ParameterException(JSONResponses.unknownAccount(accountId));
        }
        return account;
    }

    @SuppressWarnings("unused")
    public static List<Account> getAccounts(HttpServletRequest req) throws ParameterException {
        String[] accountValues = req.getParameterValues("account");
        if (accountValues == null || accountValues.length == 0) {
            throw new ParameterException(MISSING_ACCOUNT);
        }
        List<Account> result = new ArrayList<>();
        for (String accountValue : accountValues) {
            if (accountValue == null || accountValue.equals("")) {
                continue;
            }
            try {
                Account account = Account.getAccount(Convert.parseAccountId(accountValue));
                if (account == null) {
                    throw new ParameterException(UNKNOWN_ACCOUNT);
                }
                result.add(account);
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_ACCOUNT);
            }
        }
        return result;
    }

    public static int getTimestamp(HttpServletRequest req) throws ParameterException {
        return getInt(req, "timestamp", 0, Integer.MAX_VALUE, false);
    }

    public static int getFirstIndex(HttpServletRequest req) {
        try {
            int firstIndex = Integer.parseInt(req.getParameter("firstIndex"));
            return Math.max(firstIndex, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getLastIndex(HttpServletRequest req) {
        int lastIndex = Integer.MAX_VALUE;
        try {
            lastIndex = Integer.parseInt(req.getParameter("lastIndex"));
            if (lastIndex < 0) {
                lastIndex = Integer.MAX_VALUE;
            }
        } catch (NumberFormatException ignored) {}
        if (!API.checkPassword(req)) {
            int firstIndex = Math.min(getFirstIndex(req), Integer.MAX_VALUE - API.maxRecords + 1);
            lastIndex = Math.min(lastIndex, firstIndex + API.maxRecords - 1);
        }
        return lastIndex;
    }

    public static int getNumberOfConfirmations(HttpServletRequest req) throws ParameterException {
        return getInt(req, "numberOfConfirmations", 0, Nxt.getBlockchain().getHeight(), false);
    }

    public static int getHeight(HttpServletRequest req) throws ParameterException {
        return getInt(req, "height", 0, Nxt.getBlockchain().getHeight(), -1);
    }

    public static int getHeight(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return isMandatory ? getInt(req, "height", 0, Nxt.getBlockchain().getHeight(), true)
                : getInt(req, "height", 0, Nxt.getBlockchain().getHeight(), -1);
    }

    public static HoldingType getHoldingType(HttpServletRequest req) throws ParameterException {
        return HoldingType.get(ParameterParser.getByte(req, "holdingType", (byte) 0, (byte) 2, false));
    }

    public static long getHoldingId(HttpServletRequest req) throws ParameterException {
        return ParameterParser.getUnsignedLong(req, "holding", true);
    }

    public static String getAccountProperty(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String property = Convert.emptyToNull(req.getParameter("property"));
        if (property == null && isMandatory) {
            throw new ParameterException(MISSING_PROPERTY);
        }
        return property;
    }

    public static ChainTransactionId getChainTransactionId(HttpServletRequest req, String name) throws ParameterException {
        String value = Convert.emptyToNull(req.getParameter(name));
        if (value == null) {
            return null;
        }
        return getChainTransactionId(value);
    }

    public static List<ChainTransactionId> getChainTransactionIds(HttpServletRequest req, String name) throws ParameterException {
        String[] values = req.getParameterValues(name);
        if (values == null) {
            return Collections.emptyList();
        }
        List<ChainTransactionId> result = new ArrayList<>();
        for (String value : values) {
            result.add(getChainTransactionId(value));
        }
        return result;
    }

    public static ChainTransactionId getChainTransactionId(String value) throws ParameterException {
        String[] s = value.split(":");
        if (s.length != 2) {
            throw new ParameterException(JSONResponses.incorrect(value, "must be in chainId:fullHash format"));
        }
        try {
            int chainId = Integer.parseInt(s[0]);
            Chain chain = Chain.getChain(chainId);
            if (chain == null) {
                throw new ParameterException(UNKNOWN_CHAIN);
            }
            byte[] hash = Convert.parseHexString(s[1]);
            if (hash == null || hash.length != 32) {
                throw new ParameterException(JSONResponses.incorrect(value, "invalid fullHash length"));
            }
            return new ChainTransactionId(chainId, hash);
        } catch (NumberFormatException e) {
            throw new ParameterException(JSONResponses.incorrect(value, "must be in chainId:fullHash format"));
        }
    }

    public static String getSearchQuery(HttpServletRequest req) throws ParameterException {
        String query = Convert.nullToEmpty(req.getParameter("query")).trim();
        String tags = Convert.nullToEmpty(req.getParameter("tag")).trim();
        if (query.isEmpty() && tags.isEmpty()) {
            throw new ParameterException(JSONResponses.missing("query", "tag"));
        }
        if (!tags.isEmpty()) {
            StringJoiner stringJoiner = new StringJoiner(" AND TAGS:", "TAGS:", "");
            for (String tag : Search.parseTags(tags, 0, Integer.MAX_VALUE, Integer.MAX_VALUE)) {
                stringJoiner.add(tag);
            }
            query = stringJoiner.toString() + (query.isEmpty() ? "" : (" AND (" + query + ")"));
        }
        return query;
    }

    public static Transaction.Builder parseTransaction(String transactionJSON, String transactionBytes, String prunableAttachmentJSON) throws ParameterException {
        if (transactionBytes == null && transactionJSON == null) {
            throw new ParameterException(MISSING_TRANSACTION_BYTES_OR_JSON);
        }
        if (transactionBytes != null && transactionJSON != null) {
            throw new ParameterException(either("transactionBytes", "transactionJSON"));
        }
        if (prunableAttachmentJSON != null && transactionBytes == null) {
            throw new ParameterException(JSONResponses.missing("transactionBytes"));
        }
        if (transactionJSON != null) {
            try {
                JSONObject json = (JSONObject) JSONValue.parseWithException(transactionJSON);
                return Nxt.newTransactionBuilder(json);
            } catch (NxtException.ValidationException | RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionJSON");
                throw new ParameterException(response);
            }
        } else {
            try {
                byte[] bytes = Convert.parseHexString(transactionBytes);
                JSONObject prunableAttachments = prunableAttachmentJSON == null ? null : (JSONObject)JSONValue.parseWithException(prunableAttachmentJSON);
                return Nxt.newTransactionBuilder(bytes, prunableAttachments);
            } catch (NxtException.ValidationException|RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionBytes");
                throw new ParameterException(response);
            }
        }
    }

    public static Appendix getPlainMessage(HttpServletRequest req) throws ParameterException {
        return getPlainMessage(req, "true".equalsIgnoreCase(req.getParameter("messageIsPrunable")));
    }

    public static Appendix getPlainMessage(HttpServletRequest req, boolean prunable) throws ParameterException {
        String messageValue = Convert.emptyToNull(req.getParameter("message"));
        String messageIsTextStr = Convert.emptyToNull(req.getParameter("messageIsText"));
        boolean messageIsText = !"false".equalsIgnoreCase(messageIsTextStr);
        if (messageValue != null) {
            try {
                if (prunable) {
                    return new PrunablePlainMessageAppendix(messageValue, messageIsText);
                } else {
                    return new MessageAppendix(messageValue, messageIsText);
                }
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_ARBITRARY_MESSAGE);
            }
        }
        if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
            return null;
        }
        FileData fileData = getFileData(req, "messageFile",false);
        if (fileData == null) {
            return null;
        }
        byte[] message = fileData.getData();
        if (messageIsTextStr == null) {
            String detectedMimeType = Search.detectMimeType(message);
            if (detectedMimeType != null) {
                messageIsText = detectedMimeType.startsWith("text/");
            }
            if (messageIsText && !Convert.isUtf8Text(message)) {
                messageIsText = false;
            }
        } else {
            if (messageIsText && !Convert.isUtf8Text(message)) {
                throw new ParameterException(JSONResponses.incorrect("messageFile",
                        "does not contain UTF-8 text"));
            }
        }
        if (prunable) {
            return new PrunablePlainMessageAppendix(message, messageIsText);
        } else {
            return new MessageAppendix(message, messageIsText);
        }
    }

    public static Appendix getEncryptedMessage(HttpServletRequest req, Account recipient) throws ParameterException {
        return getEncryptedMessage(req, recipient, "true".equalsIgnoreCase(req.getParameter("encryptedMessageIsPrunable")));
    }

    public static Appendix getEncryptedMessage(HttpServletRequest req, Account recipient, boolean prunable) throws ParameterException {
        String isTextStr = Convert.emptyToNull(req.getParameter("messageToEncryptIsText"));
        boolean isText = !"false".equalsIgnoreCase(isTextStr);
        boolean compress = !"false".equalsIgnoreCase(req.getParameter("compressMessageToEncrypt"));
        byte[] plainMessageBytes = null;
        byte[] recipientPublicKey = null;
        EncryptedData encryptedData = ParameterParser.getEncryptedData(req, "encryptedMessage");
        if (encryptedData == null) {
            String plainMessage = Convert.emptyToNull(req.getParameter("messageToEncrypt"));
            if (plainMessage == null) {
                if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
                    return null;
                }
                FileData fileData = getFileData(req, "messageToEncryptFile",false);
                if (fileData == null) {
                    return null;
                }
                plainMessageBytes = fileData.getData();
                if (isTextStr == null) {
                    String detectedMimeType = Search.detectMimeType(plainMessageBytes);
                    if (detectedMimeType != null) {
                        isText = detectedMimeType.startsWith("text/");
                    }
                    if (isText && !Convert.isUtf8Text(plainMessageBytes)) {
                        isText = false;
                    }
                } else {
                    if (isText && !Convert.isUtf8Text(plainMessageBytes)) {
                        throw new ParameterException(JSONResponses.incorrect("messageToEncryptFile",
                                "does not contain UTF-8 text"));
                    }
                }
            } else {
                try {
                    plainMessageBytes = isText ? Convert.toBytes(plainMessage) : Convert.parseHexString(plainMessage);
                } catch (RuntimeException e) {
                    throw new ParameterException(INCORRECT_MESSAGE_TO_ENCRYPT);
                }
            }
            if (recipient != null) {
                recipientPublicKey = Account.getPublicKey(recipient.getId());
            }
            if (recipientPublicKey == null) {
                recipientPublicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter("recipientPublicKey")));
            }
            if (recipientPublicKey == null) {
                throw new ParameterException(MISSING_RECIPIENT_PUBLIC_KEY);
            }
            byte[] privateKey = getPrivateKey(req, false);
            if (privateKey != null) {
                encryptedData = Account.encryptTo(privateKey, recipientPublicKey, plainMessageBytes, compress);
            }
        }
        if (encryptedData != null) {
            if (prunable) {
                return new PrunableEncryptedMessageAppendix(encryptedData, isText, compress);
            } else {
                return new EncryptedMessageAppendix(encryptedData, isText, compress);
            }
        } else {
            if (prunable) {
                return new UnencryptedPrunableEncryptedMessageAppendix(plainMessageBytes, isText, compress, recipientPublicKey);
            } else {
                return new UnencryptedEncryptedMessageAppendix(plainMessageBytes, isText, compress, recipientPublicKey);
            }
        }
    }

    public static TaggedDataAttachment getTaggedData(HttpServletRequest req) throws ParameterException, NxtException.NotValidException {
        String name = Convert.emptyToNull(req.getParameter("name"));
        String description = Convert.nullToEmpty(req.getParameter("description"));
        String tags = Convert.nullToEmpty(req.getParameter("tags"));
        String type = Convert.nullToEmpty(req.getParameter("type")).trim();
        String channel = Convert.nullToEmpty(req.getParameter("channel"));
        boolean isText = !"false".equalsIgnoreCase(req.getParameter("isText"));
        String filename = Convert.nullToEmpty(req.getParameter("filename")).trim();
        String dataValue = Convert.emptyToNull(req.getParameter("data"));
        byte[] data;
        if (dataValue == null) {
            FileData fileData = getFileData(req,"file", true);
            if (fileData == null) {
                throw new ParameterException(INCORRECT_TAGGED_DATA_FILE);
            }
            data = fileData.getData();
            // Depending on how the client submits the form, the filename, can be a regular parameter
            // or encoded in the multipart form. If its not a parameter we take from the form
            if (filename.isEmpty() && fileData.getFilename() != null) {
                filename = fileData.getFilename().trim();
            }
            if (name == null) {
                name = filename;
            }
        } else {
            data = isText ? Convert.toBytes(dataValue) : Convert.parseHexString(dataValue);
        }

        String detectedMimeType = Search.detectMimeType(data, filename);
        if (detectedMimeType != null) {
            isText = detectedMimeType.startsWith("text/");
            if (type.isEmpty()) {
                type = detectedMimeType.substring(0, Math.min(detectedMimeType.length(), Constants.MAX_TAGGED_DATA_TYPE_LENGTH));
            }
        }

        if (name == null) {
            throw new ParameterException(MISSING_NAME);
        }
        name = name.trim();
        if (name.length() > Constants.MAX_TAGGED_DATA_NAME_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_NAME);
        }

        if (description.length() > Constants.MAX_TAGGED_DATA_DESCRIPTION_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_DESCRIPTION);
        }

        if (tags.length() > Constants.MAX_TAGGED_DATA_TAGS_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_TAGS);
        }

        type = type.trim();
        if (type.length() > Constants.MAX_TAGGED_DATA_TYPE_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_TYPE);
        }

        channel = channel.trim();
        if (channel.length() > Constants.MAX_TAGGED_DATA_CHANNEL_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_CHANNEL);
        }

        if (data.length == 0) {
            throw new ParameterException(INCORRECT_DATA_ZERO_LENGTH);
        }

        if (data.length > Constants.MAX_TAGGED_DATA_DATA_LENGTH) {
            throw new ParameterException(INCORRECT_DATA_TOO_LONG);
        }

        if (filename.length() > Constants.MAX_TAGGED_DATA_FILENAME_LENGTH) {
            throw new ParameterException(INCORRECT_TAGGED_DATA_FILENAME);
        }
        return new TaggedDataAttachment(name, description, tags, type, channel, isText, filename, data);
    }

    public static FileData getFileData(HttpServletRequest req, String paramName, boolean isMandatory) throws ParameterException {
        if (req.getContentType() == null || !req.getContentType().startsWith("multipart/form-data")) {
            if (isMandatory) {
                throw new ParameterException(INCORRECT_FILE);
            } else {
                return null;
            }
        }
        try {
            Part part = req.getPart(paramName);
            if (part == null) {
                if (isMandatory) {
                    throw new ParameterException(INCORRECT_FILE);
                } else {
                    return null;
                }
            }
            return new FileData(part);
        } catch (IOException | ServletException e) {
            Logger.logDebugMessage("error in reading file data", e);
            throw new ParameterException(INCORRECT_FILE);
        }
    }

    public static Chain getChain(HttpServletRequest request) throws ParameterException {
        return getChain(request, true);
    }

    public static Chain getChain(HttpServletRequest request, boolean isMandatory) throws ParameterException {
        return getChain(request, "chain", isMandatory);
    }

    public static Chain getChain(HttpServletRequest request, String name, boolean isMandatory) throws ParameterException {
        String chainName = Convert.emptyToNull(request.getParameter(name));
        if (chainName != null) {
            Chain chain = Chain.getChain(chainName.toUpperCase(Locale.ROOT));
            if (chain == null) {
                try {
                    chain = Chain.getChain(Integer.parseInt(chainName));
                } catch (NumberFormatException ignore) {}
                if (chain == null) {
                    throw new ParameterException(JSONResponses.unknown(name));
                }
            }
            return chain;
        } else if (isMandatory) {
            throw new ParameterException(JSONResponses.missing(name));
        }
        return null;
    }

    public static ChildChain getChildChain(HttpServletRequest request) throws ParameterException {
        return getChildChain(request, true);
    }

    public static ChildChain getChildChain(HttpServletRequest request, boolean isMandatory) throws ParameterException {
        String chainName = Convert.emptyToNull(request.getParameter("chain"));
        if (chainName != null) {
            ChildChain chain = ChildChain.getChildChain(chainName.toUpperCase(Locale.ROOT));
            if (chain == null) {
                try {
                    chain = ChildChain.getChildChain(Integer.parseInt(chainName));
                } catch (NumberFormatException ignore) {}
                if (chain == null) {
                    throw new ParameterException(UNKNOWN_CHAIN);
                }
            }
            return chain;
        } else if (isMandatory) {
            throw new ParameterException(MISSING_CHAIN);
        }
        return null;
    }

    public static PermissionType getPermissionName(HttpServletRequest request, boolean isMandatory) throws ParameterException {
        String string = Convert.emptyToNull(request.getParameter("permission"));
        if (string != null) {
            try {
                return PermissionType.valueOf(string.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignore) {
                throw new ParameterException(UNKNOWN_PERMISSION);
            }
        } else if (isMandatory) {
            throw new ParameterException(MISSING_PERMISSION);
        }
        return null;
    }

    private ParameterParser() {} // never

    public static class FileData {
        private final String filename;
        private final byte[] data;

        public FileData(Part part) throws ParameterException {
            try {
                try (InputStream is = part.getInputStream()) {
                    int nRead;
                    byte[] bytes = new byte[1024];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while ((nRead = is.read(bytes, 0, bytes.length)) != -1) {
                        baos.write(bytes, 0, nRead);
                    }
                    data = baos.toByteArray();
                }
                filename = part.getSubmittedFileName();
            } catch (IOException e) {
                Logger.logDebugMessage("error in reading file data " + part.getSubmittedFileName(), e);
                throw new ParameterException(JSONResponses.INCORRECT_FILE);
            }
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getData() {
            return data;
        }

    }

    public static JSONObject parseVoucher(byte[] data) throws ParameterException {
        // Parse the voucher
        JSONObject voucherJson;
        try {
            voucherJson = (JSONObject)JSONValue.parseWithException(Convert.toString(data));
            return parseVoucher(voucherJson);
        } catch (ParseException e) {
            return voucherError("Incorrect voucher " + e, e);
        }
    }

    public static JSONObject parseVoucher(JSONObject voucherJson) throws ParameterException {
        // Verify the voucher bytes signature
        String unsignedTransactionBytesHex = ((String) voucherJson.get("unsignedTransactionBytes"));
        if (!Crypto.verify(Convert.parseHexString((String)voucherJson.get("signature")),
                Convert.parseHexString(unsignedTransactionBytesHex),
                Convert.parseHexString((String)voucherJson.get("publicKey")))) {
            return voucherError("Cannot verify voucher signature", null);
        }

        // Verify that the transaction JSON matches the transaction bytes
        JSONObject transactionJSON = (JSONObject)voucherJson.get("transactionJSON");
        try {
            Transaction transaction = ParameterParser.parseTransaction(transactionJSON.toJSONString(), null, null).build();
            transactionJSON = transaction.getJSONObject();
        } catch (NxtException.NotValidException e) {
            return voucherError("Invalid voucher JSON", e);
        }
        JSONObject transactionFromBytes;
        try {
            Transaction transaction = ParameterParser.parseTransaction(null, unsignedTransactionBytesHex, ((JSONObject)transactionJSON.get("attachment")).toJSONString()).build();
            transactionFromBytes = transaction.getJSONObject();
        } catch (NxtException.NotValidException e) {
            return voucherError("Invalid voucher Bytes", e);
        }
        if (!transactionJSON.equals(transactionFromBytes)) {
            return voucherError(String.format("Voucher transaction bytes data %s differ from transaction json %s", transactionFromBytes, transactionJSON), null);
        }
        return voucherJson;
    }

    private static JSONObject voucherError(String message, Exception e) throws ParameterException {
        if (e != null) {
            Logger.logErrorMessage(message, e);
        } else {
            Logger.logErrorMessage(message);
        }
        JSONObject response = new JSONObject();
        response.put("errorCode", 4);
        response.put("errorDescription", message);
        throw new ParameterException(response);
    }

    public static PhasingParams parsePhasingParams(HttpServletRequest req, String parameterPrefix) throws ParameterException {
        return parsePhasingParams(req, parameterPrefix, false);
    }

    private static PhasingParams parsePhasingParams(HttpServletRequest req, String parameterPrefix, boolean isSubVoting) throws ParameterException {
        byte votingModel = ParameterParser.getByte(req, parameterPrefix + "VotingModel",
                (byte) VoteWeighting.VotingModel.MIN_CODE, (byte)VoteWeighting.VotingModel.MAX_CODE, true);
        long quorum = ParameterParser.getLong(req, parameterPrefix + "Quorum", 0, Long.MAX_VALUE, false);
        long minBalance = ParameterParser.getLong(req, parameterPrefix + "MinBalance", 0, Long.MAX_VALUE, false);
        byte minBalanceModel = ParameterParser.getByte(req, parameterPrefix + "MinBalanceModel", (byte)0, (byte)3, false);
        long holdingId = ParameterParser.getUnsignedLong(req, parameterPrefix + "Holding", false);
        if (holdingId == 0 &&
                (votingModel == VoteWeighting.VotingModel.COIN.getCode() || minBalanceModel == VoteWeighting.MinBalanceModel.COIN.getCode())) {
            holdingId = ParameterParser.getChain(req).getId();
        }
        long[] whitelist = null;
        String[] whitelistValues = req.getParameterValues(parameterPrefix + "Whitelisted");
        if (whitelistValues != null && whitelistValues.length > 0) {
            whitelist = new long[whitelistValues.length];
            for (int i = 0; i < whitelistValues.length; i++) {
                whitelist[i] = Convert.parseAccountId(whitelistValues[i]);
                if (whitelist[i] == 0) {
                    throw new ParameterException(INCORRECT_WHITELIST);
                }
            }
        }
        VoteWeighting voteWeighting = new VoteWeighting(votingModel, holdingId, minBalance, minBalanceModel);

        List<ChainTransactionId> linkedTransactionIds;
        PhasingParams.HashVoting hashVoting;
        if (parameterPrefix.startsWith("phasing")) {
            linkedTransactionIds = ParameterParser.getChainTransactionIds(req, parameterPrefix + "LinkedTransaction");
            byte[] hashedSecret = Convert.parseHexString(Convert.emptyToNull(req.getParameter(parameterPrefix + "HashedSecret")));
            byte algorithm = ParameterParser.getByte(req, parameterPrefix + "HashedSecretAlgorithm", (byte) 0, Byte.MAX_VALUE, false);
            hashVoting = new PhasingParams.HashVoting(hashedSecret, algorithm);
        } else {
            linkedTransactionIds = Collections.emptyList();
            hashVoting = null;
        }

        PhasingParams.PropertyVoting senderPropertyVoting = parsePropertyVoting(req, parameterPrefix + "Sender");
        PhasingParams.PropertyVoting recipientPropertyVoting = parsePropertyVoting(req, parameterPrefix + "Recipient");

        PhasingParams.CompositeVoting compositeVoting = null;
        if (votingModel == VoteWeighting.VotingModel.COMPOSITE.getCode()) {
            if (isSubVoting) {
                throw new ParameterException(JSONResponses.error("Sub-polls cannot have sub-polls"));
            }
            if (quorum != 1) {
                throw new ParameterException(incorrect("quorum"));
            }
            String expressionParamName = parameterPrefix + "Expression";
            String expressionStr = Convert.emptyToNull(req.getParameter(expressionParamName));
            if (expressionStr == null) {
                throw new ParameterException(missing(expressionParamName));
            }
            BooleanExpression expression = new BooleanExpression(expressionStr);

            if (expression.hasErrors(true)) {
                throw new ParameterException(JSONResponses.booleanExpressionError(expression));
            }

            Set<String> variableNames = expression.getVariables();
            SortedMap<String, PhasingParams> subPolls = new TreeMap<>();
            JSONObject subPollsJson = ParameterParser.getJson(req, "phasingSubPolls");
            PhasingParams subPollParams;
            if (subPollsJson != null) {
                for (String name : variableNames) {
                    JSONObject subPollJson = (JSONObject)JSONValue.parse((String)subPollsJson.get(name));
                    subPolls.put(name, new PhasingParams(subPollJson));
                }
            } else {
                for (String name : variableNames) {
                    name = name.trim();
                    try {
                        subPollParams = parsePhasingParams(req, parameterPrefix + name, true);
                    } catch(ParameterException e) {
                        // Add the subPoll to the error response (ugly)
                        JSONStreamAware errorResponse = e.getErrorResponse();
                        StringWriter sw = new StringWriter();
                        try {
                            errorResponse.writeJSONString(sw);
                            JSONObject subPollError = (JSONObject)JSONValue.parse(sw.toString());
                            subPollError.put("subPoll", name);
                            throw new ParameterException(JSON.prepare(subPollError));
                        } catch (IOException ioe) {
                            throw new IllegalStateException(ioe);
                        }
                    }
                    subPolls.put(name, subPollParams);
                }
            }
            compositeVoting = new PhasingParams.CompositeVoting(expressionStr, subPolls);
        }
        
        return new PhasingParams(voteWeighting, quorum,  whitelist, linkedTransactionIds, hashVoting, compositeVoting,
                senderPropertyVoting, recipientPropertyVoting);
    }

    private static PhasingParams.PropertyVoting parsePropertyVoting(HttpServletRequest req, String parameterPrefix) throws ParameterException {
        long propertySetterId = ParameterParser.getAccountId(req, parameterPrefix + "PropertySetter", false);
        String propertyName = Convert.nullToEmpty(req.getParameter(parameterPrefix + "PropertyName")).trim();
        String propertyValue = Convert.nullToEmpty(req.getParameter(parameterPrefix + "PropertyValue")).trim();
        return new PhasingParams.PropertyVoting(propertySetterId, propertyName, propertyValue);
    }

    public static List<Bundler.Filter> getBundlingFilters(HttpServletRequest req) {
        String[] filterStrings = req.getParameterValues("filter");
        List<Bundler.Filter> filters;
        if (filterStrings != null) {
            filters = new ArrayList<>(filterStrings.length);
            for (String filterStr : filterStrings) {
                String[] nameAndParameter = filterStr.split(":", 2);
                String name = nameAndParameter[0];
                String parameter = null;
                if (nameAndParameter.length == 2) {
                    parameter = nameAndParameter[1];
                }
                filters.add(Bundler.createBundlingFilter(name, parameter));
            }
        } else {
            filters = Collections.emptyList();
        }
        return filters;
    }

    public static TransactionPriority getPriority(HttpServletRequest req, String name, TransactionPriority defaultValue) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return defaultValue;
        }
        try {
            int ordinal = Integer.parseInt(paramValue);
            if (ordinal >= 0 && ordinal < TransactionPriority.values().length) {
                return TransactionPriority.values()[ordinal];
            } else {
                throw new ParameterException(incorrect(name, String.format("value %s not in range [0, %d]",
                        paramValue, TransactionPriority.values().length - 1)));
            }
        } catch (NumberFormatException nfe) {
            try {
                return TransactionPriority.valueOf(paramValue);
            } catch (IllegalArgumentException iae) {
                throw new ParameterException(incorrect(name, String.format("value %s is not one of %s",
                        paramValue, Arrays.toString(TransactionPriority.values()))));
            }
        }
    }

    private static final int[] MINIMUM_VERSION = new int[] { 0,0,0 };

    public static int[] parseVersionParameter(String version) {
        if (version.equals("")) {
            return MINIMUM_VERSION;
        } else {
            if (version.endsWith("e")) {
                version = version.substring(0, version.length() - 1);
            }
            return Arrays.stream((version.split("\\."))).mapToInt(Integer::parseInt).toArray();
        }
    }
}
