package com.higgsblock.global.chain.app.schedule;

import com.higgsblock.global.chain.app.blockchain.BlockIndex;
import com.higgsblock.global.chain.app.blockchain.BlockService;
import com.higgsblock.global.chain.app.blockchain.listener.MessageCenter;
import com.higgsblock.global.chain.app.consensus.syncblock.Inventory;
import com.higgsblock.global.chain.app.service.impl.BlockDaoService;
import com.higgsblock.global.chain.app.service.impl.BlockIdxDaoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author baizhengwen
 * @date 2018/3/23
 */
@Slf4j
@Component
public class InventoryTask extends BaseTask {

    @Autowired
    private MessageCenter messageCenter;

    @Autowired
    private BlockService blockService;

    @Autowired
    private BlockIdxDaoService blockIdxDaoService;

    @Override
    protected void task() {
        long height = blockService.getBestMaxHeight();
        Inventory inventory = new Inventory();
        inventory.setHeight(height);
        BlockIndex blockIndex = blockIdxDaoService.getBlockIndexByHeight(height);
        if (blockIndex != null &&
                CollectionUtils.isNotEmpty(blockIndex.getBlockHashs())) {
            Set<String> set = new HashSet<>(blockIndex.getBlockHashs());
            inventory.setHashs(set);
        }
        messageCenter.broadcast(inventory);
    }

    @Override
    protected long getPeriodMs() {
        return TimeUnit.SECONDS.toMillis(3);
    }
}