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

package nxt.ms;

import nxt.crypto.HashFunction;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class CurrencyMinting {

    public static final Set<HashFunction> acceptedHashFunctions =
            Collections.unmodifiableSet(EnumSet.of(HashFunction.SHA256, HashFunction.SHA3, HashFunction.SCRYPT, HashFunction.Keccak25));

    public static boolean meetsTarget(long accountId, Currency currency, CurrencyMintingAttachment attachment) {
        byte[] hash = getHash(currency.getAlgorithm(), attachment.getNonce(), attachment.getCurrencyId(), attachment.getUnitsQNT(),
                attachment.getCounter(), accountId);
        byte[] target = getTarget(currency.getMinDifficulty(), currency.getMaxDifficulty(),
                attachment.getUnitsQNT(), currency.getCurrentSupplyQNT() - currency.getReserveSupplyQNT(),
                currency.getMaxSupplyQNT() - currency.getReserveSupplyQNT());
        return meetsTarget(hash, target);
    }

    public static boolean meetsTarget(byte[] hash, byte[] target) {
        for (int i = hash.length - 1; i >= 0; i--) {
            if ((hash[i] & 0xff) > (target[i] & 0xff)) {
                return false;
            }
            if ((hash[i] & 0xff) < (target[i] & 0xff)) {
                return true;
            }
        }
        return true;
    }

    public static byte[] getHash(byte algorithm, long nonce, long currencyId, long unitsQNT, long counter, long accountId) {
        HashFunction hashFunction = HashFunction.getHashFunction(algorithm);
        return getHash(hashFunction, nonce, currencyId, unitsQNT, counter, accountId);
    }

    public static byte[] getHash(HashFunction hashFunction, long nonce, long currencyId, long unitsQNT,
            long counter, long accountId) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8 + 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(nonce);
        buffer.putLong(currencyId);
        buffer.putLong(unitsQNT);
        buffer.putLong(counter);
        buffer.putLong(accountId);
        return hashFunction.hash(buffer.array());
    }

    public static byte[] getTarget(int min, int max, long unitsQNT, long currentMintableSupplyQNT, long totalMintableSupplyQNT) {
        return getTarget(getNumericTarget(min, max, unitsQNT, currentMintableSupplyQNT, totalMintableSupplyQNT));
    }

    public static byte[] getTarget(BigInteger numericTarget) {
        byte[] targetRowBytes = numericTarget.toByteArray();
        if (targetRowBytes.length == 32) {
            return reverse(targetRowBytes);
        }
        byte[] targetBytes = new byte[32];
        Arrays.fill(targetBytes, 0, 32 - targetRowBytes.length, (byte) 0);
        System.arraycopy(targetRowBytes, 0, targetBytes, 32 - targetRowBytes.length, targetRowBytes.length);
        return reverse(targetBytes);
    }

    public static BigInteger getNumericTarget(Currency currency, long unitsQNT) {
        return getNumericTarget(currency.getMinDifficulty(), currency.getMaxDifficulty(), unitsQNT,
                currency.getCurrentSupplyQNT() - currency.getReserveSupplyQNT(),
                currency.getMaxSupplyQNT() - currency.getReserveSupplyQNT());
    }

    public static BigInteger getNumericTarget(int min, int max, long unitsQNT,
            long currentMintableSupplyQNT, long totalMintableSupplyQNT) {
        if (min < 1 || max > 255) {
            throw new IllegalArgumentException(String.format("Min: %d, Max: %d, allowed range is 1 to 255", min, max));
        }
        int exp = (int)(256 - min - ((max - min) * currentMintableSupplyQNT) / totalMintableSupplyQNT);
        return BigInteger.valueOf(2).pow(exp).subtract(BigInteger.ONE).divide(BigInteger.valueOf(unitsQNT));
    }

    private static byte[] reverse(byte[] b) {
        for(int i=0; i < b.length/2; i++) {
            byte temp = b[i];
            b[i] = b[b.length - i - 1];
            b[b.length - i - 1] = temp;
        }
        return b;
    }

    private CurrencyMinting() {} // never

}
