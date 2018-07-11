package com.higgsblock.global.chain.app.blockchain;

import com.higgsblock.global.chain.app.blockchain.transaction.TransactionService;
import com.higgsblock.global.chain.common.enums.SystemCurrencyEnum;
import com.higgsblock.global.chain.common.utils.ExecutorServices;
import com.higgsblock.global.chain.network.PeerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @program: HiggsGlobal
 * @description:
 * @author: yezaiyong
 * @create: 2018-06-29 20:32
 **/
@Service
@Scope("prototype")
@Slf4j
public class WitnessCountTime {

    private static long preHeight = 0;
    private static long currHeight = 0;
    private static long curSec;
    private ExecutorService executorService;
    private volatile boolean isRunning;
    static Block block;
    public volatile static boolean isCurrBlockConfirm = false;
    public static final long WAIT_WITNESS_TIME = 150;


    @Autowired
    private BlockService blockService;
    @Autowired
    private PeerManager peerManager;
    @Autowired
    private TransactionService transactionService;

    public boolean queryCurrHeightStartTime() throws InterruptedException {
        String address = peerManager.getSelf().getId();
        if (BlockService.WITNESS_ADDRESS_LIST.contains(address)) {
            currHeight = blockService.getMaxHeight();
            return startTimer();
        }
        return false;
    }

    public static boolean isCurrBlockConfirm(Block block) {
        try {
            WitnessCountTime.block = block;
            TimeUnit.SECONDS.sleep(3);
            return isCurrBlockConfirm;
        } catch (InterruptedException e) {
            LOGGER.error("isCurrBlockConfirm error {}", e);
        }
        return false;
    }

    public static void instantiationBlock(Block block) {
        WitnessCountTime.block = null;
        WitnessCountTime.curSec = 0;
        preHeight = block.getHeight();
        currHeight = block.getHeight();
    }

    public boolean startTimer() throws InterruptedException {
        if (block == null) {
            preHeight = currHeight;
            WitnessCountTime.curSec = 0;
            if (executorService == null) {
                this.start(ExecutorServices.newSingleThreadExecutor(getClass().getName(), 1));
            }
        }
        return true;
    }

    public final synchronized void start(ExecutorService executorService) {
        if (!isRunning) {
            this.executorService = executorService;
            this.executorService.execute(() -> {
                while (isRunning) {
                    try {
                        ++curSec;
                        LOGGER.info("curSec = " + curSec);
                        TimeUnit.SECONDS.sleep(1);
                        if (block == null) {
                            if (preHeight >= currHeight) {
                                LOGGER.info("block is null  pre >= curr;pre = " + preHeight + " curr =" + currHeight);
                            } else {
                                preHeight = currHeight;
                                WitnessCountTime.curSec = 0;
                            }
                        } else {
                            if (preHeight >= block.getHeight()) {
                                isCurrBlockConfirm = false;
                                continue;
                            }
                            if (curSec >= WAIT_WITNESS_TIME) {
                                isCurrBlockConfirm = false;
                                if (verifyBlockBelongCommonMiner(block)) {
                                    isCurrBlockConfirm = true;
                                }
                                continue;
                            }
                            isCurrBlockConfirm = false;
                        }
                    } catch (InterruptedException e) {
                        LOGGER.error("startCountTime InterruptedException", e);
                    }
                }
            });
            isRunning = true;
        }

    }

    public boolean verifyBlockBelongCommonMiner(Block block) {
        return transactionService.hasStake(block.getMinerFirstPKSig().getAddress(), SystemCurrencyEnum.CMINER);
    }
}