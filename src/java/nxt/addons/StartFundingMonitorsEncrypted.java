/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2019 Jelurida IP B.V.
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

package nxt.addons;

import nxt.http.APITag;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;

public final class StartFundingMonitorsEncrypted extends StartEncrypted {

    public String getAPIRequestType() {
        return "startFundingMonitorsEncrypted";
    }

    @Override
    protected APITag getAPITag() {
        return APITag.ACCOUNTS;
    }

    @Override
    protected JSONStreamAware processDecrypted(BufferedReader reader) throws IOException, ParseException {
        JSONArray monitors = StartFundingMonitors.startFundingMonitors((JSONObject) JSONValue.parseWithException(reader));
        JSONObject response = new JSONObject();
        response.put("monitors", monitors);
        return response;
    }

}

