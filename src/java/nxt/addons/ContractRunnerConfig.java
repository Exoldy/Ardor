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

package nxt.addons;

import nxt.crypto.EncryptedData;
import nxt.peer.FeeRateCalculator;

public interface ContractRunnerConfig {

    byte[] getPublicKey();

    String getPublicKeyHexString();

    long getAccountId();

    String getAccount();

    String getAccountRs();

    boolean isAutoFeeRate();

    FeeRateCalculator.TransactionPriority getAutoFeeRatePriority();

    long getMinBundlerBalanceFXT();

    long getMinBundlerFeeLimitFQT();

    long getFeeRateNQTPerFXT(int chainId);

    long getCurrentFeeRateNQTPerFXT(int chainId);

    JO getParams();

    boolean isValidator();

    int getCatchUpInterval();

    int getMaxSubmittedTransactionsPerInvocation();

    byte[] getRunnerSeed();

    ContractProvider getContractProvider();

    EncryptedData encryptTo(byte[] publicKey, byte[] data, boolean compress);

    byte[] decryptFrom(byte[] publicKey, EncryptedData encryptedData, boolean uncompress);

    byte[] getPrivateKey();

    byte[] getValidatorPrivateKey();

    String getStatus();

    void init(JO config);
}
