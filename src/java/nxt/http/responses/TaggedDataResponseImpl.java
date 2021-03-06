/*
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

package nxt.http.responses;

import nxt.addons.JO;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.util.List;

public class TaggedDataResponseImpl implements TaggedDataResponse {

    private final byte[] transactionFullHash;
    private final long account;
    private final String name;
    private final String description;
    private final String tags;
    private final List<String> parsedTags;
    private final String type;
    private final String channel;
    private final String filename;
    private final boolean isText;
    private final byte[] data;
    private final int transactionTimestamp;
    private final int blockTimestamp;

    TaggedDataResponseImpl(JSONObject response) {
        this(new JO(response));
    }

    TaggedDataResponseImpl(JO taggedDataJson) {
        transactionFullHash = taggedDataJson.parseHexString("transactionFullHash");
        account = taggedDataJson.getEntityId("account");
        name = taggedDataJson.getString("name");
        description = taggedDataJson.getString("description");
        tags = taggedDataJson.getString("tags");
        parsedTags = taggedDataJson.getArray("parsedTags").values();
        type = taggedDataJson.getString("type");
        channel = taggedDataJson.getString("channel");
        filename = taggedDataJson.getString("filename");
        isText = taggedDataJson.getBoolean("isText");
        data = Convert.toBytes(taggedDataJson.getString("data"), isText);
        transactionTimestamp = taggedDataJson.getInt("transactionTimestamp");
        blockTimestamp = taggedDataJson.getInt("blockTimestamp");
    }

    @Override
    public byte[] getTransactionFullHash() {
        return transactionFullHash;
    }

    @Override
    public long getAccount() {
        return account;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getTags() {
        return tags;
    }

    @Override
    public List<String> getParsedTags() {
        return parsedTags;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public boolean isText() {
        return isText;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public int getTransactionTimestamp() {
        return transactionTimestamp;
    }

    @Override
    public int getBlockTimestamp() {
        return blockTimestamp;
    }
}
