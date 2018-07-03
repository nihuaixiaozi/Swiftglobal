package com.higgsblock.global.chain.app.service;

import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.blockchain.transaction.Transaction;
import com.higgsblock.global.chain.app.blockchain.transaction.UTXO;

import java.util.List;

/**
 * The interface Trans service.
 *
 * @author Zhao xiaogang
 * @date 2018 -05-22
 */
public interface ITransService {

    /**
     * Add transaction index and utxo in database
     *
     * @param bestBlock     the best block
     * @param bestBlockHash the best block hash
     * @throws Exception the exception
     */
    void addTransIdxAndUtxo(Block bestBlock, String bestBlockHash) throws Exception;

    /**
     * Remove double spent transaction
     *
     * @param cacheTransactions cached transactions
     * @return void
     */
    void removeDoubleSpendTx(List<Transaction> cacheTransactions);

    /**
     * Gets utxo.
     *
     * @param utxoKey the utxo key
     * @return the utxo
     */
    UTXO getUTXO(String utxoKey);

    /**
     * Compute miner balance. Refresh the miner's amount
     *
     * @param block the best block
     */
    void computeMinerBalance(Block block);
}
