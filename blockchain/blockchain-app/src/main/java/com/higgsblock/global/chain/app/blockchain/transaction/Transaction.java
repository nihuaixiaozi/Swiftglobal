package com.higgsblock.global.chain.app.blockchain.transaction;

import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.higgsblock.global.chain.app.blockchain.PubKeyAndSignPair;
import com.higgsblock.global.chain.app.common.message.Message;
import com.higgsblock.global.chain.app.constants.EntityType;
import com.higgsblock.global.chain.app.entity.BaseBizEntity;
import com.higgsblock.global.chain.app.utils.JsonSizeCounter;
import com.higgsblock.global.chain.app.utils.SizeCounter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.util.List;

/**
 * @author yuguojia
 * @date 2018/03/08
 **/
@Data
@Slf4j
@NoArgsConstructor
@Message(EntityType.TRANSACTION)
public class Transaction extends BaseBizEntity {

    private static final int LIMITED_SIZE_UNIT = 1024 * 100;
    private static final int EXTRA_LIMITED_SIZE_UNIT = 1024 * 10;

    /**
     * the hash of this transaction
     */
    protected String hash;
    /**
     * lock after pointed block height of time
     */
    protected long lockTime;
    /**
     * extra info for this transaction
     */
    protected String extra;
    /**
     * sign of this transaction
     */
    protected PubKeyAndSignPair pubKeyAndSignPair;
    /**
     * the sources of current spending
     */
    private List<TransactionInput> inputs;
    /**
     * transfer to other coin
     */
    private List<TransactionOutput> outputs;

    /**
     * the timestamp of this transaction created
     */
    private long transactionTime = System.currentTimeMillis();

    private String creatorPubKey;


    @Override
    public  boolean valid(){

        if(StringUtils.isEmpty(hash)){
            return false;
        }

        if (StringUtils.isEmpty(creatorPubKey)){
            return false;
        }

        if (lockTime < 0){
            return false;
        }

        //todo yezaiyong check this only if the input is empty
//        if (pubKeyAndSignPair != null){
//            //valid Sign
//            if (!ECKey.verifySign(hash,pubKeyAndSignPair.getSignature(),pubKeyAndSignPair.getPubKey())){
//                LOGGER.error("Transaction signature is error ");
//                return false;
//            }
//        }

        if (CollectionUtils.isNotEmpty(inputs)){
            for (TransactionInput input: inputs) {
                if (!input.valid()){
                    return false;
                }
            }
        }

        if (CollectionUtils.isNotEmpty(outputs)){
            for (TransactionOutput out: outputs) {
                if (!out.valid()){
                    return false;
                }
            }
        }
        return  true;
    }


    public String getHash() {
        if (StringUtils.isBlank(hash)) {
            HashFunction function = Hashing.sha256();
            StringBuilder builder = new StringBuilder();
            builder.append(function.hashInt(version));
            builder.append(function.hashLong(transactionTime));
            builder.append(function.hashLong(lockTime));
            builder.append(function.hashString(null == extra ? Strings.EMPTY : extra, Charsets.UTF_8));
            builder.append(function.hashString(null == creatorPubKey ? Strings.EMPTY : creatorPubKey, Charsets.UTF_8));
            if (CollectionUtils.isNotEmpty(inputs)) {
                inputs.forEach((input) -> {
                    TransactionOutPoint prevOut = input.getPrevOut();
                    if (prevOut != null) {
                        builder.append(function.hashLong(prevOut.getIndex()));
                        String prevOutHash = prevOut.getHash();
                        builder.append(function.hashString(null == prevOutHash ? Strings.EMPTY : prevOutHash, Charsets.UTF_8));
                    }
                });
            } else {
                builder.append(function.hashInt(0));
            }
            if (CollectionUtils.isNotEmpty(outputs)) {
                outputs.forEach((output) -> builder.append(output.getHash()));
            } else {
                builder.append(function.hashInt(0));
            }
            hash = function.hashString(builder, Charsets.UTF_8).toString();
        }
        return hash;
    }

    public TransactionOutput getTransactionOutputByIndex(short index) {
        int size = outputs.size();
        if (size <= index + 1 || index < 0) {
            return null;
        }
        return outputs.get(index);
    }

    public boolean sizeAllowed() {
        SizeCounter sizeCounter = new JsonSizeCounter();
        if (sizeCounter.calculateSize(this.extra) > EXTRA_LIMITED_SIZE_UNIT) {
            return false;
        }
        if (sizeCounter.calculateSize(this) > LIMITED_SIZE_UNIT) {
            return false;
        }
        return true;
    }

    public boolean isEmptyInputs() {
        if (CollectionUtils.isEmpty(inputs)) {
            return true;
        }
        return false;
    }
}