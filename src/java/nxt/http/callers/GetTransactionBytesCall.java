// Auto generated code, do not modify
package nxt.http.callers;

import nxt.http.APICall;

public class GetTransactionBytesCall extends APICall.Builder<GetTransactionBytesCall> {
    private GetTransactionBytesCall() {
        super(ApiSpec.getTransactionBytes);
    }

    public static GetTransactionBytesCall create() {
        return new GetTransactionBytesCall();
    }

    public static GetTransactionBytesCall create(int chain) {
        return new GetTransactionBytesCall().param("chain", chain);
    }

    public GetTransactionBytesCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetTransactionBytesCall fullHash(String fullHash) {
        return param("fullHash", fullHash);
    }

    public GetTransactionBytesCall fullHash(byte[] fullHash) {
        return param("fullHash", fullHash);
    }

    public GetTransactionBytesCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
