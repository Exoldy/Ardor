// Auto generated code, do not modify
package nxt.http.callers;

import nxt.http.APICall;

public class GetPollResultCall extends APICall.Builder<GetPollResultCall> {
    private GetPollResultCall() {
        super(ApiSpec.getPollResult);
    }

    public static GetPollResultCall create() {
        return new GetPollResultCall();
    }

    public static GetPollResultCall create(int chain) {
        return new GetPollResultCall().param("chain", chain);
    }

    public GetPollResultCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetPollResultCall holding(String holding) {
        return param("holding", holding);
    }

    public GetPollResultCall holding(long holding) {
        return unsignedLongParam("holding", holding);
    }

    public GetPollResultCall minBalance(String minBalance) {
        return param("minBalance", minBalance);
    }

    public GetPollResultCall votingModel(String votingModel) {
        return param("votingModel", votingModel);
    }

    public GetPollResultCall poll(String poll) {
        return param("poll", poll);
    }

    public GetPollResultCall poll(long poll) {
        return unsignedLongParam("poll", poll);
    }

    public GetPollResultCall minBalanceModel(String minBalanceModel) {
        return param("minBalanceModel", minBalanceModel);
    }

    public GetPollResultCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
