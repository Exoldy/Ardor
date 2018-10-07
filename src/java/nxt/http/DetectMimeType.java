/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2018 Jelurida IP B.V.
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

import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.Search;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.INCORRECT_TAGGED_DATA_FILE;

public final class DetectMimeType extends APIServlet.APIRequestHandler {

    static final DetectMimeType instance = new DetectMimeType();

    private DetectMimeType() {
        super("file", new APITag[] {APITag.DATA, APITag.UTILS}, "data", "filename", "isText");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        String filename = Convert.nullToEmpty(req.getParameter("filename")).trim();
        String dataValue = Convert.emptyToNull(req.getParameter("data"));
        byte[] data;
        if (dataValue == null) {
            ParameterParser.FileData fileData = ParameterParser.getFileData(req, "file", true);
            if (fileData == null) {
                throw new ParameterException(INCORRECT_TAGGED_DATA_FILE);
            }
            data = fileData.getData();
            // Depending on how the client submits the form, the filename, can be a regular parameter
            // or encoded in the multipart form. If its not a parameter we take it from the form
            if (filename.isEmpty() && fileData.getFilename() != null) {
                filename = fileData.getFilename();
            }
        } else {
            boolean isText = !"false".equalsIgnoreCase(req.getParameter("isText"));
            data = isText ? Convert.toBytes(dataValue) : Convert.parseHexString(dataValue);
        }

        JSONObject response = new JSONObject();
        response.put("type", Search.detectMimeType(data, filename));
        return response;
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}
