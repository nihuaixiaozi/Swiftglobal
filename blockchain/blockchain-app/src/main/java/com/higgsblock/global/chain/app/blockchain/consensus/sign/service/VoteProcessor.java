package com.higgsblock.global.chain.app.blockchain.consensus.sign.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.blockchain.BlockProcessor;
import com.higgsblock.global.chain.app.blockchain.BlockWitness;
import com.higgsblock.global.chain.app.blockchain.consensus.message.VoteTable;
import com.higgsblock.global.chain.app.blockchain.consensus.message.VotingBlockRequest;
import com.higgsblock.global.chain.app.blockchain.consensus.vote.Vote;
import com.higgsblock.global.chain.app.blockchain.listener.MessageCenter;
import com.higgsblock.global.chain.app.common.event.BlockPersistedEvent;
import com.higgsblock.global.chain.app.common.event.ReceiveOrphanBlockEvent;
import com.higgsblock.global.chain.common.eventbus.listener.IEventBusListener;
import com.higgsblock.global.chain.crypto.ECKey;
import com.higgsblock.global.chain.crypto.KeyPair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author yangyi
 * @deta 2018/4/26
 * @description
 */
@Service
@Slf4j
public class VoteProcessor implements IEventBusListener {

    @Autowired
    private KeyPair keyPair;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private MessageCenter messageCenter;

    public static final int MAX_SIZE = 5;

    private static final int MIN_VOTE = 7;

    private static final int MIN_SAME_SIGN = 7;

    private Cache<Long, Map<Integer, Set<Vote>>> voteCache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .build();

    @Getter
    private long height;

    /**
     * the row is version of vote,the column is the pubKey of vote,
     * the inner key of Map is the blockHash of the vote
     */
    private VoteTable voteTable = null;

    @Getter
    private Cache<Long, Map<String, Block>> blockCache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .build();

    private Block blockWithEnoughSign;

    @Subscribe
    public void process(BlockPersistedEvent event) {
        initWitnessTask(event.getHeight() + 1L);
    }

    public synchronized void initWitnessTask(long height) {
        if (height < this.height) {
            return;
        }
        String pubKey = keyPair.getPubKey();
        String address = ECKey.pubKey2Base58Address(pubKey);
        if (BlockProcessor.WITNESS_ADDRESS_LIST.contains(address)) {
            if (height == this.height) {
                dealVoteCache();
                return;
            }
            LOGGER.info("start the witness task for height {}", height);
            this.height = height;
            this.voteTable = new VoteTable(new HashMap<>());
            this.blockWithEnoughSign = null;
            this.blockCache.get(height, k -> new HashMap<>()).values().forEach(this::voteFirstVote);
            voteCache.invalidate(height - 3);
            blockCache.invalidate(height - 3);
            LOGGER.info("height {},init witness task success", this.height);
        }
    }

    public synchronized void addOriginalBlock(Block block) {
        if (block == null) {
            return;
        }
        if (this.height > block.getHeight()) {
            return;
        }

        this.blockCache.get(block.getHeight(), k -> new HashMap<>()).put(block.getHash(), block);
        voteFirstVote(block);
        dealVoteCache();
    }

    private void voteFirstVote(Block block) {
        if (this.height != block.getHeight()) {
            return;
        }
        Map<String, Vote> voteMap = this.voteTable.getVoteMap(1, keyPair.getPubKey());
        if (voteMap == null || voteMap.size() == 0) {
            String blockHash = block.getHash();
            long blockHeight = block.getHeight();
            LOGGER.info("start vote first vote,height {}, {}", blockHeight, blockHash);
            String bestBlockHash = block.getHash();
            LOGGER.info("add source block from miner and vote {},{}", this.height, bestBlockHash);
            int voteVersion = 1;
            String proofPubKey = null;
            String proofBlockHash = null;
            int proofVersion = 0;
            Vote vote = createVote(bestBlockHash, block.getHeight(), voteVersion, proofBlockHash, proofPubKey, proofVersion, null);
            this.voteTable.addVote(vote);
            this.messageCenter.dispatchToWitnesses(SerializationUtils.clone(voteTable));
            LOGGER.info("send voteHashTable to witness success {},{}", this.height, voteTable);
        }
    }

    private void dealVoteCache() {
        LOGGER.info("deal vote cache");
        Map<Integer, Set<Vote>> cache = voteCache.getIfPresent(this.height);
        if (null == cache || cache.isEmpty()) {
            return;
        }
        int rowSize = cache.size();
        int startAllVoteSize = this.voteTable.getAllVoteSize();
        for (int version = 1; version <= rowSize; version++) {
            if (version > 1 && this.voteTable.getVoteMapOfPubKeyByVersion(version - 1).size() < MIN_VOTE) {
                return;
            }
            int startARowVoteSize = this.voteTable.getARowVoteSize(version);
            Set<Vote> set = cache.get(version);
            Set<Vote> setTemp = new HashSet<>(11);
            Set<Vote> leaderVotes = new HashSet<>(11);
            Set<Vote> followerVotes = new HashSet<>(11);
            set.forEach(vote -> {
                if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion(), vote.getWitnessPubKey()))
                        .map(map -> map.containsKey(vote.getBlockHash())).orElse(false)) {
                    return;
                }
                if (vote.isLeaderVote()) {
                    leaderVotes.add(vote);
                } else {
                    followerVotes.add(vote);
                }
            });
            leaderVotes.forEach(vote -> {
                if (vote.getVoteVersion() == 1) {
                    if (!blockCache.get(this.height, k -> new HashMap<>()).containsKey(vote.getBlockHash())) {
                        Set<String> set1 = new HashSet<>();
                        set1.add(vote.getBlockHash());
                        messageCenter.dispatchToWitnesses(new VotingBlockRequest(set1));
                        setTemp.add(vote);
                        return;
                    }
                } else if (vote.getVoteVersion() > 1) {
                    if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getProofVersion(), vote.getProofPubKey()))
                            .map(map -> !map.containsKey(vote.getProofBlockHash())).orElse(true)) {
                        setTemp.add(vote);
                        return;
                    }
                    if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion() - 1, vote.getWitnessPubKey()))
                            .map(map -> !map.containsKey(vote.getPreBlockHash())).orElse(true)) {
                        setTemp.add(vote);
                        return;
                    }
                }
                this.voteTable.addVote(vote);
            });
            followerVotes.forEach(vote -> {
                if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getProofVersion(), vote.getProofPubKey()))
                        .map(map -> !map.containsKey(vote.getProofBlockHash())).orElse(true)) {
                    setTemp.add(vote);
                    return;
                }
                if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion() - 1, vote.getWitnessPubKey()))
                        .map(map -> !map.containsKey(vote.getPreBlockHash())).orElse(true)) {
                    setTemp.add(vote);
                    return;
                }
                this.voteTable.addVote(vote);
            });
            this.voteCache.get(this.height, k -> new HashMap<>()).put(version, setTemp);
            if (this.voteTable.getARowVoteSize(version) > startARowVoteSize && collectionVoteSign(version, height)) {
                return;
            }
        }
        if (this.voteTable.getAllVoteSize() > startAllVoteSize) {
            LOGGER.info("local voteHashTable with height {} ,is : {}", height, voteTable);
            messageCenter.dispatchToWitnesses(SerializationUtils.clone(voteTable));
        }
    }

    private void updateVoteCache(long height, VoteTable otherVoteTable) {
        Map<Integer, Map<String, Map<String, Vote>>> voteMap = otherVoteTable.getVoteTable();
        voteMap.values().stream().forEach(map -> {
            if (MapUtils.isEmpty(map)) {
                return;
            }
            map.values().forEach(map1 -> {
                if (null == map1) {
                    return;
                }
                map1.values().forEach(vote -> {
                    if (null == vote || !vote.valid()) {
                        return;
                    }
                    if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion(), vote.getWitnessPubKey()))
                            .map(map2 -> map2.containsKey(vote.getBlockHash())).orElse(false)) {
                        return;
                    }
                    voteCache.get(height, k -> new HashMap<>(6)).compute(vote.getVoteVersion(), (k, v) -> {
                        if (null == v) {
                            v = new HashSet<>();
                        }
                        v.add(vote);
                        return v;
                    });
                });
            });
        });
    }

    public synchronized void dealVoteTable(String sourceId, long voteHeight, VoteTable otherVoteTable) {

        boolean isOver = this.height > voteHeight || (this.height == voteHeight && blockWithEnoughSign != null);
        if (isOver) {
            LOGGER.info("the voting process of {} is over , current height : {}", voteHeight, height);
            return;
        }
        if (voteHeight > this.height) {
            eventBus.post(new ReceiveOrphanBlockEvent(height, null, sourceId));
            LOGGER.info("the height is greater than local , sync block");
            return;
        }

        if (!checkOriginalBlock(sourceId, voteHeight, otherVoteTable)) {
            return;
        }

        int startAllVoteSize = voteTable.getAllVoteSize();
        dealVoteTable(voteHeight, otherVoteTable);
        if (voteTable.getAllVoteSize() > startAllVoteSize) {
            LOGGER.info("local voteHashTable with height {} ,is : {}", voteHeight, voteTable);
            messageCenter.dispatchToWitnesses(SerializationUtils.clone(voteTable));
        }
    }

    private boolean checkOriginalBlock(String sourceId, long voteHeight, VoteTable otherVoteTable) {
        Map<String, Map<String, Vote>> map = otherVoteTable.getVoteMapOfPubKeyByVersion(1);
        if (null == map || map.isEmpty()) {
            return false;
        }
        Set<String> blockHashs = new HashSet<>();
        map.values().forEach(map1 -> {
            if (null == map1) {
                return;
            }
            map1.forEach((k, v) -> {
                if (!blockCache.get(height, kk -> new HashMap<>()).containsKey(k)) {
                    blockHashs.add(k);
                }
            });
        });
        if (blockHashs.size() > 0) {
            updateVoteCache(voteHeight, otherVoteTable);
            if (null != sourceId) {
                messageCenter.unicast(sourceId, new VotingBlockRequest(blockHashs));
            } else {
                messageCenter.dispatchToWitnesses(new VotingBlockRequest(blockHashs));
            }
            LOGGER.info("source blocks is not enough,add vote table to cache");
            return false;
        }
        return true;
    }

    private void dealVoteTable(long voteHeight, VoteTable otherVoteTable) {
        LOGGER.info("add voteMap to task with voteHeight {} ,otherVoteTable {}", voteHeight, otherVoteTable);

        int versionSize = otherVoteTable.getVersionSize();
        for (int version = 1; version <= versionSize; version++) {
            Map<String, Map<String, Vote>> newRows = otherVoteTable.getVoteMapOfPubKeyByVersion(version);
            if (MapUtils.isEmpty(newRows)) {
                return;
            }
            if (version > 1 && otherVoteTable.getVoteMapOfPubKeyByVersion(version - 1).size() < MIN_VOTE) {
                LOGGER.warn("pre version's vote number < {}", MIN_VOTE);
                return;
            }

            int startARowVoteSize = this.voteTable.getARowVoteSize(version);
            Set<Vote> leaderVotes = new HashSet<>(11);
            Set<Vote> followerVotes = new HashSet<>(11);
            newRows.values().forEach(v -> {
                if (null == v || v.isEmpty()) {
                    return;
                }
                v.values().forEach(vote -> {
                    if (null == vote || !vote.valid()) {
                        return;
                    }
                    if (vote.isLeaderVote()) {
                        leaderVotes.add(vote);
                    } else {
                        followerVotes.add(vote);
                    }
                });
            });
            if (leaderVotes.isEmpty()) {
                return;
            }
            dealVotes(version, leaderVotes);
            dealVotes(version, followerVotes);
            if (this.voteTable.getARowVoteSize(version) > startARowVoteSize && collectionVoteSign(version, voteHeight)) {
                return;
            }
        }
    }

    private void dealVotes(int version, Set<Vote> set) {
        set.forEach(vote -> {
            if (version != vote.getVoteVersion()) {
                return;
            }
            if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion(), vote.getWitnessPubKey()))
                    .map(map -> map.containsKey(vote.getBlockHash())).orElse(false)) {
                return;
            }
            if (version > 1) {
                if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getProofVersion(), vote.getProofPubKey()))
                        .map(map -> !map.containsKey(vote.getProofBlockHash())).orElse(true)) {
                    return;
                }
                if (Optional.ofNullable(this.voteTable.getVoteMap(vote.getVoteVersion() - 1, vote.getWitnessPubKey()))
                        .map(map -> !map.containsKey(vote.getPreBlockHash())).orElse(true)) {
                    return;
                }
            }
            this.voteTable.addVote(vote);
        });
    }

    private boolean collectionVoteSign(int version, long voteHeight) {
        Map<String, Map<String, Vote>> rowVersion = this.voteTable.getVoteMapOfPubKeyByVersion(version);
        if (rowVersion == null || rowVersion.size() == 0) {
            LOGGER.info("the vote is empty {},{}", this.height, version);
            return false;
        }
        //row is blockHash,column is pubKey and value is sign
        Table<String, String, String> voteSignTable = HashBasedTable.create();
        Set<Map.Entry<String, Map<String, Vote>>> voteEntrySet = rowVersion.entrySet();
        LOGGER.info("the version is {},the voteHeight is {} and the votes are {}", version, voteHeight, voteEntrySet);
        String bestBlockHash = null;
        for (Map.Entry<String, Map<String, Vote>> voteEntry : voteEntrySet) {
            Map<String, Vote> voteEntryValue = voteEntry.getValue();
            String votePubKey = voteEntry.getKey();
            if (voteEntryValue == null || voteEntryValue.size() != 1) {
                LOGGER.info("height {},version {},the {} has {} vote", voteHeight, votePubKey, version, voteEntryValue == null ? 0 : voteEntryValue.size());
                continue;
            }
            Vote vote = voteEntryValue.values().iterator().next();
            if (vote == null) {
                continue;
            }
            String voteBlockHash = vote.getBlockHash();
            String voteSign = vote.getSignature();
            if (StringUtils.isBlank(bestBlockHash)) {
                bestBlockHash = voteBlockHash;
                LOGGER.info("height {},version {},the version is {},set the bestBlockHash to {}", voteHeight, version, bestBlockHash);
            } else {
                if (bestBlockHash.compareTo(voteBlockHash) < 0) {
                    bestBlockHash = voteBlockHash;
                    LOGGER.info("height {},version {},change the bestBlockHash to {}", voteHeight, version, bestBlockHash);
                } else if (bestBlockHash.compareTo(voteBlockHash) == 0) {
                    LOGGER.info("height {},version {},the voteBlockHash is equals the bestBlockHash {}", voteHeight, version, bestBlockHash);
                } else {
                    LOGGER.info("height {},version {},the bestBlockHash do'nt change {},{}", voteHeight, version, bestBlockHash, voteBlockHash);
                }
            }
            voteSignTable.put(voteBlockHash, votePubKey, voteSign);
            Map<String, String> voteRow = voteSignTable.row(voteBlockHash);
            if (voteRow.size() >= MIN_SAME_SIGN) {
                blockWithEnoughSign = blockCache.get(height, k -> new HashMap<>()).get(voteBlockHash);
                if (null == blockWithEnoughSign) {
                    LOGGER.info("the block lost , blockHash : {}", voteBlockHash);
                    return false;
                }
                LOGGER.info("height {},version {},there have enough sign for block {}", voteHeight, version, voteBlockHash);
                List<BlockWitness> blockWitnesses = new LinkedList<>();
                Iterator<Map.Entry<String, String>> iterator = voteRow.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> next = iterator.next();
                    BlockWitness blockWitness = new BlockWitness();
                    blockWitness.setPubKey(next.getKey());
                    blockWitness.setSignature(next.getValue());
                    blockWitnesses.add(blockWitness);
                }
                blockWithEnoughSign.setVoteVersion(version);
                blockWithEnoughSign.setOtherWitnessSigPKS(blockWitnesses);
                LOGGER.info("height {},version {},vote result is {}", voteHeight, version, blockWithEnoughSign);
                messageCenter.dispatchToWitnesses(SerializationUtils.clone(voteTable));
                this.messageCenter.broadcast(blockWithEnoughSign);
                return true;
            }
        }
        LOGGER.info("height {},version {},there haven't enough sign, bestBlockHash is {}", voteHeight, version, bestBlockHash);
        String proofBlockHash = bestBlockHash;
        if (StringUtils.isBlank(bestBlockHash)) {
            LOGGER.info("height {},version {},the bestBlockHash is blank{}", voteHeight, version);
            return false;
        }
        String proofPubKey = voteSignTable.row(bestBlockHash).keySet().iterator().next();
        Map<String, Vote> voteMap = this.voteTable.getVoteMap(version, keyPair.getPubKey());
        if (voteMap == null || voteMap.size() == 0) {
            int proofVersion = version;
            Map<String, Vote> preVoteMap = this.voteTable.getVoteMap(version - 1, keyPair.getPubKey());
            String preBlockHash = preVoteMap.keySet().iterator().next();
            bestBlockHash = bestBlockHash.compareTo(preBlockHash) < 0 ? preBlockHash : bestBlockHash;
            Vote vote = createVote(bestBlockHash, voteHeight, version, proofBlockHash, proofPubKey, proofVersion, preBlockHash);
            LOGGER.info("height {},version {},vote the current version {}", voteHeight, version, vote);
            this.voteTable.addVote(vote);
            return false;
        }
        if (rowVersion.size() < MIN_VOTE) {
            LOGGER.info("there haven't enough vote to check sign {},{},current the number of vote is {}", this.height, version, rowVersion.size());
            return false;
        }
        String currentVersionVoteBlockHash = voteMap.keySet().iterator().next();
        if (StringUtils.equals(currentVersionVoteBlockHash, bestBlockHash)) {
            return false;
        }
        version++;
        voteMap = this.voteTable.getVoteMap(version, keyPair.getPubKey());
        if (voteMap == null || voteMap.size() == 0) {
            int proofVersion = version - 1;
            Vote vote = createVote(bestBlockHash, voteHeight, version, proofBlockHash, proofPubKey, proofVersion, currentVersionVoteBlockHash);
            LOGGER.info("height {},version {},vote the next version {}", voteHeight, version, vote);
            this.voteTable.addVote(vote);
            return false;
        }
        return false;
    }

    private Vote createVote(String bestBlockHash, long voteHeight, int version, String proofBlockHash, String proofPubKey, int proofVersion, String preBlockHash) {
        Vote vote = new Vote();
        vote.setBlockHash(bestBlockHash);
        vote.setHeight(voteHeight);
        vote.setVoteVersion(version);
        vote.setWitnessPubKey(keyPair.getPubKey());
        vote.setProofPubKey(proofPubKey);
        vote.setProofVersion(proofVersion);
        vote.setProofBlockHash(proofBlockHash);
        vote.setPreBlockHash(preBlockHash);
        String msg = BlockProcessor.getWitnessSingMessage(vote.getHeight(), vote.getBlockHash(), vote.getVoteVersion());
        String sign = ECKey.signMessage(msg, keyPair.getPriKey());
        vote.setSignature(sign);
        return vote;
    }

    public boolean isExistInBlockCache(long height, String hash) {
        Map<String, Block> map = blockCache.getIfPresent(height);
        return null != map && map.containsKey(hash);
    }
}