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

package nxt.http;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.CombineSecretCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SplitSecretCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SecretSharingApiTest extends BlockchainTest {
    @Test
    public void useApis2of3() {
        // Generate the pieces
        JO splitResponse = SplitSecretCall.create().secretPhrase(ALICE.getSecretPhrase()).totalPieces(3).minimumPieces(2).call();

        // Select pieces and combine
        List<String> pieces = splitResponse.getArray("pieces").values();
        JO combineResponse = CombineSecretCall.create().pieces(pieces.get(0), pieces.get(1)).call();
        Assert.assertEquals(ALICE.getSecretPhrase(), combineResponse.getString("secretPhrase"));

        // Select other pieces and combine
        combineResponse = CombineSecretCall.create().pieces(pieces.get(0), pieces.get(2)).call();
        Assert.assertEquals(ALICE.getSecretPhrase(), combineResponse.getString("secretPhrase"));
        combineResponse = CombineSecretCall.create().pieces(pieces.get(1), pieces.get(2)).call();
        Assert.assertEquals(ALICE.getSecretPhrase(), combineResponse.getString("secretPhrase"));
    }

    @Test
    public void useApis3of5() {
        // Generate the pieces
        JO splitResponse = SplitSecretCall.create().secretPhrase(CHUCK.getSecretPhrase()).totalPieces(5).minimumPieces(3).call();

        // Select pieces and combine
        List<String> pieces = splitResponse.getArray("pieces").values();
        Logger.logInfoMessage(pieces.toString());
        JO combineResponse = CombineSecretCall.create().pieces(pieces.get(1), pieces.get(3), pieces.get(4)).call();
        Assert.assertEquals(CHUCK.getSecretPhrase(), combineResponse.getString("secretPhrase"));
    }

    @Test
    public void missingPieces() {
        // Generate the pieces
        JO splitResponse = SplitSecretCall.create().secretPhrase(ALICE.getSecretPhrase()).totalPieces(3).minimumPieces(2).call();

        // Select pieces and combine
        List<String> pieces = splitResponse.getArray("pieces").values();
        JO combineResponse = CombineSecretCall.create().pieces(pieces.get(0)).call();
        Assert.assertNotEquals(ALICE.getSecretPhrase(), combineResponse.getString("secretPhrase"));
    }

    @Test
    public void wrongPieces() {
        // Generate the pieces
        JO splitResponse = SplitSecretCall.create().secretPhrase(CHUCK.getSecretPhrase()).totalPieces(3).minimumPieces(2).call();

        // Select pieces and combine correctly
        List<String> pieces = splitResponse.getArray("pieces").values();
        JO combineResponse = CombineSecretCall.create().pieces(pieces.get(0), pieces.get(1)).call();
        Assert.assertEquals(CHUCK.getSecretPhrase(), combineResponse.getString("secretPhrase"));

        // Now corrupt one of the pieces and see that the reproduced secret is wrong
        combineResponse = CombineSecretCall.create().pieces(pieces.get(0) + "AB", pieces.get(1)).call();
        Assert.assertNotEquals(CHUCK.getSecretPhrase(), combineResponse.getString("secretPhrase"));
    }

    /**
     * Submit a transaction with one secret phrase piece and the account id.
     * The other piece is loaded from nxt.properties and combined with this piece.
     * Piece values can be generated using the useApis2of3 test
     */
    @Test
    public void submitSecretPhrasePiece() {
        JO sendMoneyResponse = SendMoneyCall.create(2).recipient(BlockchainTest.BOB.getRsAccount()).amountNQT(123456789).feeNQT(1000000).
                sharedPiece("1:9999:3:2:0:3:626cef7a2bfe67d3ecc2168c32e8a460db5c2c71adaed3b9").sharedPieceAccount(ALICE.getRsAccount()).call();
        Assert.assertEquals("true", sendMoneyResponse.getString("broadcasted"));
    }

    /**
     * Submit a transaction with multiple secret phrase pieces.
     * Nothing is loaded from nxt.properties.
     * Piece values can be generated by the useApis3of5 test
     */
    @Test
    public void submitMultipleSecretPhrasePiece() {
        JO sendMoneyResponse = SendMoneyCall.create(2).recipient(BlockchainTest.BOB.getRsAccount()).amountNQT(123456789).feeNQT(1000000).
                sharedPiece("0:-1797511508:5:3:0:1:00994e4109d68fba8ed92c1b65b7c50963d4480f623de5d5d1f230eb199ac37c088f3bc3",
                        "0:-1797511508:5:3:0:2:01135524eff764421052eb34520b6f009588ad5bb3f707443dacccab51b430283513ee4a",
                        "0:-1797511508:5:3:0:5:042599fb5a1d4876b15b8e1705864273d5395e7f05f3e2bd163c5a2b4cf34adade2c0e33").
                sharedPieceAccount(CHUCK.getRsAccount()).call();
        Assert.assertEquals("true", sendMoneyResponse.getString("broadcasted"));
    }
}
