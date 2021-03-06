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

import nxt.addons.JO;
import nxt.blockchain.Chain;
import nxt.http.callers.ApiSpec;
import nxt.http.responses.BlockResponse;
import nxt.http.responses.TransactionResponse;
import nxt.util.Convert;
import nxt.util.ResourceLookup;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class APICall {

    private static final int IGNIS_CHAIN_ID = 2; // Avoid using the ChildChain object since this depends on the whole Nxt infrastructure

    private final APIConnector apiConnector;

    private APICall(Builder<?> builder) {
        URL remoteUrl = builder.remoteUrl;
        if (builder.isRemoteOnly() && remoteUrl == null) {
            throw new IllegalArgumentException("API call " + getClass().getName() + " must connect to a remote node");
        }
        if (remoteUrl != null) {
            apiConnector = new APIRemoteConnector(builder.params, builder.parts, remoteUrl, builder.isTrustRemoteCertificate);
        } else {
            apiConnector = new APIInProcessConnector(builder.params, builder.parts);
        }
    }

    public static class Builder<T extends Builder> {
        protected final Map<String, List<String>> params = new HashMap<>();
        private final List<String> validParams = new ArrayList<>();
        private boolean isValidationEnabled = true;
        private final Map<String, byte[]> parts = new HashMap<>();
        private final List<String> validFileParams;
        private final String requestType;
        private URL remoteUrl;
        private boolean isTrustRemoteCertificate;

        public Builder(String requestType) {
            this(ApiSpec.valueOf(requestType));
        }

        public Builder(ApiSpec apiSpec) {
            this(apiSpec.name(), apiSpec.getParameters(), apiSpec.getFileParameters(), apiSpec.isChainSpecific());
        }

        protected Builder(String requestType, List<String> validParams, List<String> validFileParams, boolean isChainSpecific) {
            this.requestType = requestType;
            this.params.put("requestType", Collections.singletonList(this.requestType));
            this.validParams.addAll(validParams);
            this.validFileParams = validFileParams;
            if (isChainSpecific) {
                this.validParams.add("chain");
                param("chain", "" + IGNIS_CHAIN_ID); // Ignis specific API
            }
        }

        public T remote(URL url) {
            remoteUrl = url;
            return self();
        }

        public T trustRemoteCertificate(boolean trustRemoteCertificate) {
            isTrustRemoteCertificate = trustRemoteCertificate;
            return self();
        }

        public boolean isRemoteOnly() {
            return false;
        }

        public T setParamValidation(boolean isEnabled) {
            isValidationEnabled = isEnabled;
            return self();
        }

        public T param(String key, String value) {
            return param(key, Collections.singletonList(value));
        }

        public T param(String key, String[] values) {
            return param(key, Arrays.asList(values));
        }

        public T param(String key, List<String> values) {
            if (isValidationEnabled && !validParams.contains(key)) {
                throw new IllegalArgumentException(String.format("Invalid parameter %s for request type %s", key, requestType));
            }
            if (values.size() == 0) {
                throw new IllegalArgumentException(String.format("Empty values parameter %s for requesttype %s", key, requestType));
            }
            params.put(key, values);
            return self();
        }

        public T param(String key, boolean value) {
            return param(key, "" + value);
        }

        public T param(String key, byte value) {
            return param(key, "" + value);
        }

        public T param(String key, int value) {
            return param(key, "" + value);
        }

        public T param(String key, int... intArray) {
            String[] stringArray = Arrays.stream(intArray).boxed().map(i -> Integer.toString(i)).toArray(String[]::new);
            return param(key, stringArray);
        }

        public T param(String key, long value) {
            return param(key, "" + value);
        }

        public T param(String key, long... longArray) {
            String[] unsignedLongs = Arrays.stream(longArray).boxed().map(l -> Long.toString(l)).toArray(String[]::new);
            return param(key, unsignedLongs);
        }

        public T unsignedLongParam(String key, long value) {
            return param(key, Long.toUnsignedString(value));
        }

        public T unsignedLongParam(String key, long... longArray) {
            String[] unsignedLongs = Arrays.stream(longArray).boxed().map(Long::toUnsignedString).toArray(String[]::new);
            return param(key, unsignedLongs);
        }

        public T param(String key, byte[] value) {
            return param(key, Convert.toHexString(value));
        }

        public T param(String key, byte[][] value) {
            String[] stringArray = new String[value.length];
            for (int i = 0; i < value.length; i++) {
                stringArray[i] = Convert.toHexString(value[i]);
            }
            return param(key, stringArray);
        }

        public T secretPhrase(String value) {
            return param("secretPhrase", value);
        }

        public T privateKey(byte[] value) {
            return privateKey(Convert.toHexString(value));
        }

        public T privateKey(String value) {
            return param("privateKey", value);
        }

        public T sharedPiece(String... sharedPiece) {
            return param("sharedPiece", sharedPiece);
        }

        public T sharedPieceAccount(String value) {
            return param("sharedPieceAccount", value);
        }

        public T chain(String chain) {
            return param("chain", chain);
        }

        public T chain(int chainId) {
            return param("chain", "" + chainId);
        }

        public T feeNQT(long value) {
            return param("feeNQT", "" + value);
        }

        public T feeRateNQTPerFXT(long value) {
            return param("feeRateNQTPerFXT", "" + value);
        }

        public T recipient(long id) {
            return param("recipient", Long.toUnsignedString(id));
        }

        public T recipient(String recipient) {
            return param("recipient", recipient);
        }

        public String getParam(String key) {
            List<String> values = params.get(key);
            return values == null ? null : values.get(0);
        }

        public Chain getChain() {
            if (!isParamSet("chain")) {
                return null;
            }
            return Chain.getChain(getParam("chain"));
        }

        public boolean isParamSet(String key) {
            return params.get(key) != null;
        }

        public T parts(String key, byte[] b) {
            if (!validFileParams.contains(key)) {
                throw new IllegalArgumentException(String.format("Invalid file parameter %s for request type %s", key, requestType));
            }
            parts.put(key, b);
            return self();
        }

        private T self() {
            return (T) this;
        }

        public APICall build() {
            return new APICall(this);
        }

        public JO call() {
            return new APICall(this).getJsonResponse();
        }

        public byte[] download() {
            return new APICall(this).getBytes();
        }

        /**
         * Use with any API which returns a "transactions" json array
         * @return list of transaction objects
         */
        public List<TransactionResponse> getTransactions() {
            return getTransactions("transactions");
        }

        /**
         * Use in case the response transaction array has a different name
         * @param arrayName the name of the transaction array
         * @return list of transaction objects
         */
        public List<TransactionResponse> getTransactions(String arrayName) {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            if (!jo.isExist(arrayName)) {
                throw new IllegalStateException("Response object does not represent a list of transactions " + jo.toJSONString());
            }
            return jo.getJoList(arrayName).stream().map(TransactionResponse::create).collect(Collectors.toList());
        }

        /**
         * Response from CreateTransaction calls wraps the transactions inside a transactionJSON object
         * @return list of transaction objects
         */
        public List<TransactionResponse> getCreatedTransactions() {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            if (!jo.isExist("transactions")) {
                throw new IllegalStateException("Response object does not represent a list of created transactions " + jo.toJSONString());
            }
            return jo.getJoList("transactions").stream().map(t -> TransactionResponse.create(t.getJo("transactionJSON"))).collect(Collectors.toList());
        }

        /**
         * Use to parse responses of APIs which return a transaction object like getTransaction
         * @return transaction object
         */
        public TransactionResponse getTransaction() {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            if (!jo.isExist("deadline")) {
                throw new IllegalStateException("Response object does not represent a transaction " + jo.toJSONString());
            }
            return TransactionResponse.create(jo);
        }

        /**
         * Use to parse responses of create transaction API
         * @return transaction object
         */
        @SuppressWarnings("unused")
        public TransactionResponse getCreatedTransaction() {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            JO transactionJSON = jo.getJo("transactionJSON");
            if (!transactionJSON.isExist("deadline")) {
                throw new IllegalStateException("Response object does not represent a transaction " + jo.toJSONString());
            }
            return TransactionResponse.create(transactionJSON);
        }

        public List<BlockResponse> getBlocks() {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            if (!jo.isExist("blocks")) {
                throw new IllegalStateException("Response object does not represent a list of blocks " + jo.toJSONString());
            }
            return jo.getJoList("blocks").stream().map(BlockResponse::create).collect(Collectors.toList());
        }

        public BlockResponse getBlock() {
            JO jo = call();
            if (jo.isExist("errorCode")) {
                throw new IllegalStateException(jo.toJSONString());
            }
            if (!jo.isExist("generatorPublicKey")) {
                throw new IllegalStateException("Response object does not represent a block " + jo.toJSONString());
            }
            return BlockResponse.create(jo);
        }
    }

    public JO getJsonResponse() {
        return new JO(invoke());
    }

    public InputStream getInputStream() {
        return apiConnector.getInputStream();
    }

    public byte[] getBytes() {
        try (InputStream is = getInputStream()) {
            return ResourceLookup.readInputStream(is);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public InvocationError invokeWithError() {
        JSONObject actual = invoke();
        return new InvocationError(actual);
    }


    public JSONObject invokeNoError() {
        JSONObject actual = invoke();

        assertNull(actual.get("errorDescription"));
        assertNull(actual.get("errorCode"));

        return actual;
    }

    public JSONObject invoke() {
        return AccessController.doPrivileged((PrivilegedAction<JSONObject>) this::invokeImpl);
    }

    private JSONObject invokeImpl() {
        try {
            InputStream inputStream = apiConnector.getInputStream();
            try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return (JSONObject) JSONValue.parseWithException(reader); // Parse the response into Json object
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertNull(Object o) {
        if (o != null) {
            throw new AssertionError("Expected null, got: " + o);
        }
    }

    private static <T> T assertNotNull(T o) {
        if (o == null) {
            throw new AssertionError("Expected not null");
        }
        return o;
    }

    public static class InvocationError {
        private final JSONObject jsonObject;

        public InvocationError(JSONObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        public long getErrorCode() {
            return getLong("errorCode");
        }

        private long getLong(String errorCode) {
            final Object object = assertNotNull(jsonObject.get(errorCode));
            if (object instanceof Long) {
                return (Long) object;
            }
            return Long.parseLong(object.toString());
        }

        public String getErrorDescription() {
            return str("errorDescription");
        }

        private String str(String errorCode) {
            return (String) assertNotNull(jsonObject.get(errorCode));
        }
    }
}
