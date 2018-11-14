package org.talust.block;

import lombok.extern.slf4j.Slf4j;
import org.talust.core.core.ECKey;
import org.talust.common.crypto.Hex;
import org.talust.common.crypto.Sha256Hash;
import org.talust.common.model.Coin;

import org.talust.core.core.AccountTool;
import org.talust.core.core.Definition;
import org.talust.core.core.NetworkParams;

import org.talust.core.data.ConsensusCalculationUtil;
import org.talust.core.model.Account;
import org.talust.core.model.Address;
import org.talust.core.model.Block;
import org.talust.core.network.MainNetworkParams;
import org.talust.core.script.ScriptBuilder;
import org.talust.core.server.NtpTimeService;
import org.talust.core.storage.BlockStorage;
import org.talust.core.storage.BlockStore;
import org.talust.core.transaction.Transaction;
import org.talust.core.transaction.TransactionInput;


import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


/**
 * 制作测试网络的创世块
 */
@Slf4j
public class MakeTestNetGengsisBlock {
    private static NetworkParams network = MainNetworkParams.get();
    private static BlockStorage blockStorage = BlockStorage.get();

    public static void main(String[] args) throws Exception {
        makeTestNetGengsisBlock();
    }

    private static void makeTestNetGengsisBlock() throws Exception {

        Block gengsisBlock = new Block(network);

        gengsisBlock.setPreHash(Sha256Hash.wrap(Hex.decode("0000000000000000000000000000000000000000000000000000000000000000")));
        gengsisBlock.setHeight(0);
        gengsisBlock.setTime(NtpTimeService.currentTimeSeconds());

        //交易列表
        List<Transaction> txs = new ArrayList<Transaction>();

        //产出货币总量
        Transaction coinBaseTx = new Transaction(network);
        coinBaseTx.setVersion(Definition.VERSION);
        coinBaseTx.setType(Definition.TYPE_COINBASE);

        TransactionInput input = new TransactionInput();
        coinBaseTx.addInput(input);
        input.setScriptSig(ScriptBuilder.createCoinbaseInputScript("this a gengsis tx".getBytes()));

        //root账户
        ECKey key = ECKey.fromPrivate(new BigInteger("53344981916733052797719979954879126010376675651411523924472355573124348278346"));
        Address address = AccountTool.newAddress(network, key);

//			Address address = Address.fromBase58(network, "toMahRViJBfKJ49QzYymKVb6JqNCLxTPN4");

        System.out.println("==========================");
        System.out.println(address.getBase58());
        System.out.println("==========================");

        coinBaseTx.addOutput(Coin.MAX.subtract(ConsensusCalculationUtil.TOTAL_REWARD), 0, address);
        coinBaseTx.verify();

        txs.add(coinBaseTx);

        //talust账户
        ECKey key1 = ECKey.fromPrivate(new BigInteger("6051327876861885131857809515399627539599799898688725081588866561552275209869"));
        Address address1 = AccountTool.newAddress(network, key1);

        //把永久锁定的转入一个账号内
        Transaction coinbase = new Transaction(network);
        coinbase.setVersion(Definition.VERSION);
        coinbase.setType(Definition.TYPE_COINBASE);

        TransactionInput inputT = new TransactionInput();
        coinbase.addInput(inputT);
        inputT.setScriptSig(ScriptBuilder.createCoinbaseInputScript("this a gengsis tx".getBytes()));

        coinbase.addOutput(Coin.MAX.subtract(ConsensusCalculationUtil.TOTAL_REWARD), 0, address);
        coinbase.verify();

        txs.add(coinbase);

        gengsisBlock.setTxs(txs);
        gengsisBlock.setTxCount(txs.size());

        Sha256Hash merkleHash = gengsisBlock.buildMerkleHash();
        System.out.println("the merkle hash is: "+ merkleHash);



        address = AccountTool.newAddress(network, key);

        System.out.println("==========================");
        System.out.println(address.getBase58());
        System.out.println("==========================");

        Account account = new Account(network);
        account.setAddress(address);
        account.setEcKey(key);
        account.setMgPubkeys(new byte[][]{ key.getPubKey()});

        gengsisBlock.sign(account);
        gengsisBlock.verifyScript();
        System.out.println("the block hash is: "+ Hex.encode(gengsisBlock.getHash().getBytes()));
        Block block = new Block(network, gengsisBlock.baseSerialize());
        Sha256Hash merkleHashRe =  block.buildMerkleHash();
        System.out.println("the merkle Hash revert is: "+ merkleHashRe);
        System.out.println("the merkle hash revert is: "+ Hex.encode(block.getHash().getBytes()));
        BlockStore blockStore = new BlockStore(network,block);
        log.info("genesis revert is: "+ Hex.encode(block.baseSerialize()));
        blockStorage.saveBlock(blockStore);
    }
}
