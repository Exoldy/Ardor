// Auto generated code, do not modify
package nxt.http.callers;

import java.lang.String;
import nxt.http.APICall;

public class GetExpectedExchangeRequestsCall extends APICall.Builder<GetExpectedExchangeRequestsCall> {
    private GetExpectedExchangeRequestsCall() {
        super("getExpectedExchangeRequests");
    }

    public static GetExpectedExchangeRequestsCall create(int chain) {
        GetExpectedExchangeRequestsCall instance = new GetExpectedExchangeRequestsCall();
        instance.param("chain", chain);
        return instance;
    }

    public GetExpectedExchangeRequestsCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetExpectedExchangeRequestsCall chain(String chain) {
        return param("chain", chain);
    }

    public GetExpectedExchangeRequestsCall chain(int chain) {
        return param("chain", chain);
    }

    public GetExpectedExchangeRequestsCall currency(String currency) {
        return param("currency", currency);
    }

    public GetExpectedExchangeRequestsCall currency(long currency) {
        return unsignedLongParam("currency", currency);
    }

    public GetExpectedExchangeRequestsCall includeCurrencyInfo(boolean includeCurrencyInfo) {
        return param("includeCurrencyInfo", includeCurrencyInfo);
    }

    public GetExpectedExchangeRequestsCall account(String account) {
        return param("account", account);
    }

    public GetExpectedExchangeRequestsCall account(long account) {
        return unsignedLongParam("account", account);
    }

    public GetExpectedExchangeRequestsCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
