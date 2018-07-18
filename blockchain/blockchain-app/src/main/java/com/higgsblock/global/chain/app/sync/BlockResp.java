package com.higgsblock.global.chain.app.sync;

import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.common.constants.EntityType;
import com.higgsblock.global.chain.app.common.message.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yuanjiantao
 * @date 7/18/2018
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Message(EntityType.GET_BLOCK_RESP)
public class BlockResp {

    private Block block;
}
