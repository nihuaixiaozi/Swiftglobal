package com.higgsblock.global.chain.app.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.higgsblock.global.chain.app.blockchain.WitnessEntity;
import com.higgsblock.global.chain.app.dao.entity.WitnessPo;
import com.higgsblock.global.chain.app.dao.iface.IWitnessEntity;
import com.higgsblock.global.chain.app.service.IWitnessEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author liuweizhen
 * @date 2018-05-21
 */
@Service
public class WitnessEntityService implements IWitnessEntityService {

    /**
     * TODO lwz pre mine block num 2018-05-26
     */
    private static final int PRE_BLOCKS = 13;

    /**
     * TODO lwz block num for a round of witness to sign 2018-05-26
     */
    private static final int BATCH = 200;

    private static final Cache<String, List<WitnessEntity>> WITNESS_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();


    @Autowired
    private IWitnessEntity iWitnessEntity;

    @Override
    public List<WitnessPo> getAll() {
        return iWitnessEntity.findAll();
    }

    private String buildCacheKey(long height) {
        Preconditions.checkArgument(height > PRE_BLOCKS, String.format("too samll height to find witness, %s", height));

        long start = PRE_BLOCKS + 1;
        long end = start + BATCH - 1;

        while (height > end) {
            start = end + 1;
            end = start + BATCH - 1;
        }


        return start + "_" + end;
    }
}
