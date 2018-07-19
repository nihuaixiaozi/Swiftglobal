package com.higgsblock.global.chain.app.blockchain.consensus.sign.service;

import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.blockchain.BlockProcessor;
import com.higgsblock.global.chain.app.blockchain.consensus.message.OriginalBlock;
import com.higgsblock.global.chain.app.blockchain.listener.MessageCenter;
import com.higgsblock.global.chain.crypto.ECKey;
import com.higgsblock.global.chain.crypto.KeyPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author yangyi
 * @date 2018/3/6
 */
@Component
@Slf4j
public class OriginBlockProcessor {

    @Autowired
    private MessageCenter messageCenter;

    @Autowired
    private VoteProcessor voteProcessor;

    @Autowired
    private KeyPair keyPair;

    /**
     * Creator sends the signed block to other witnesses for resigning.
     */
    public void sendOriginBlockToWitness(Block block) {
        LOGGER.info("send origin block to witness,height={},hash={}", block.getHeight(), block.getHash());
        OriginalBlock originalBlock = new OriginalBlock();
        originalBlock.setBlock(block);
        messageCenter.dispatchToWitnesses(originalBlock);
        if (BlockProcessor.WITNESS_ADDRESS_LIST.contains(ECKey.pubKey2Base58Address(keyPair.getPubKey()))) {
            voteProcessor.addOriginalBlock(block);
        }
    }
}
