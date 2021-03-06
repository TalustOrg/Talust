package org.talust.controller;/*
 * MIT License
 *
 * Copyright (c) 2017-2018 talust.org talust.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.util.internal.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.talust.common.crypto.Base58;
import org.talust.common.model.DepositAccount;
import org.talust.common.model.Deposits;
import org.talust.common.tools.ArithUtils;
import org.talust.core.model.Account;
import org.talust.core.model.Address;
import org.talust.core.network.MainNetworkParams;
import org.talust.service.TransferAccountService;

import java.util.List;

/**
 * @author Axe-Liu
 * @date 2018/7/31.
 */
@RestController
@RequestMapping("/api/transfer")
@Api("转账相关API")
@Slf4j
public class TransferAccountsController {
    @Autowired
    private TransferAccountService transferAccountService;

    @ApiOperation(value = "发起转账", notes = "帐户信息已经存在的情况下,转账")
    @PostMapping(value = "transfer")
    JSONObject transfer(@RequestParam String toAddress, @RequestParam String money, @RequestParam String address, @RequestParam String password) {
        JSONObject resp = new JSONObject();
        if (StringUtil.isNullOrEmpty(toAddress) || StringUtil.isNullOrEmpty(money)) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00008");
            return resp;
        }
        try {
            money = ArithUtils.mul(money, "1", 8);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00009");
            return resp;
        }
        Account account = transferAccountService.getAccountByAddress(address);
        if (null == account) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00010");
            return resp;
        }
        try {
            Base58.decodeChecked(toAddress);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00011");
            return resp;
        }
        if (account.isEncrypted()) {
            if (StringUtil.isNullOrEmpty(password)) {
                resp.put("retCode", "1");
                resp.put("msgCode", "E00012");
                return resp;
            } else {
                boolean pswCorrect = transferAccountService.decryptAccount(password, account);
                if (!pswCorrect) {
                    resp.put("retCode", "1");
                    resp.put("msgCode", "E00013");
                    return resp;
                }
            }
        }
        JSONObject isOk = transferAccountService.transfer(toAddress, money, address, password);
        return isOk;
    }

    @ApiOperation(value = "查询储蓄状态", notes = "查询某一个节点的储蓄状态")
    @PostMapping(value = "searchOneSuperNodeDeposite")
    JSONObject searchOneSuperNodeDeposite(@RequestParam String address) {
        JSONObject resp = new JSONObject();
        Address superNode = Address.fromBase58(MainNetworkParams.get(), address);
        Deposits deposits = transferAccountService.getDeposits(superNode.getHash160());
        List<DepositAccount> depositAccountList = deposits.getDepositAccounts();
        if (null == depositAccountList || depositAccountList.size() == 0) {
            resp.put("retCode", "0");
            resp.put("msgCode", "S00004");
        } else {
            resp.put("retCode", "0");
            JSONArray dataList = new JSONArray();
            for (int i = 0; i < depositAccountList.size(); i++) {
                JSONObject data = new JSONObject();
                data.put("id", i);
                data.put("amout", depositAccountList.get(i).getAmount().value);
                dataList.add(data);
            }
        }
        return resp;
    }

    @ApiOperation(value = "查询储蓄状态", notes = "查询全部节点的储蓄状态")
    @PostMapping(value = "searchAllSuperNodeDeposite")
    JSONObject searchAllSuperNodeDeposite() {
        JSONObject resp = new JSONObject();
        try {
            resp.put("retCode", "0");
            JSONArray data = transferAccountService.getAllDeposits();
            resp.put("data", data);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00014");
        }
        return resp;
    }

    @ApiOperation(value = "加入共识", notes = "加入共识")
    @PostMapping(value = "joinSuperNodeDeposite")
    JSONObject joinSuperNodeDeposite(@RequestParam String NodeAddress, @RequestParam String money, @RequestParam String password, @RequestParam String address) {
        JSONObject resp = new JSONObject();
        if (StringUtil.isNullOrEmpty(NodeAddress) || StringUtil.isNullOrEmpty(money)) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00008");
            return resp;
        }
        try {
            money = ArithUtils.mul(money, "1", 8);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00009");
            return resp;
        }
        Account account = transferAccountService.getAccountByAddress(address);
        if (null == account) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00010");
            return resp;
        }
        try {
            Base58.decodeChecked(NodeAddress);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00011");
            return resp;
        }
        if (account.isEncrypted()) {
            if (StringUtil.isNullOrEmpty(password)) {
                resp.put("retCode", "1");
                resp.put("msgCode", "E00012");
                return resp;
            } else {
                boolean pswCorrect = transferAccountService.decryptAccount(password, account);
                if (!pswCorrect) {
                    resp.put("retCode", "1");
                    resp.put("msgCode", "E00013");
                    return resp;
                }
            }
        }

        JSONObject isOk = transferAccountService.consensusJoin(NodeAddress, money, address, password);
        return isOk;
    }

    @ApiOperation(value = "退出共识", notes = "退出共识")
    @PostMapping(value = "leaveSuperNodeDeposite")
    JSONObject leaveSuperNodeDeposite(@RequestParam String NodeAddress, @RequestParam String password, @RequestParam String address) {
        JSONObject resp = new JSONObject();
        if (StringUtil.isNullOrEmpty(NodeAddress)) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00008");
            return resp;
        }
        Account account = transferAccountService.getAccountByAddress(address);
        if (null == account) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00010");
            return resp;
        }
        try {
            Base58.decodeChecked(NodeAddress);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00011");
            return resp;
        }
        if (account.isEncrypted()) {
            if (StringUtil.isNullOrEmpty(password)) {
                resp.put("retCode", "1");
                resp.put("msgCode", "E00012");
                return resp;
            } else {
                boolean pswCorrect = transferAccountService.decryptAccount(password, account);
                if (!pswCorrect) {
                    resp.put("retCode", "1");
                    resp.put("msgCode", "E00013");
                    return resp;
                }
            }
        }

        JSONObject isOk = transferAccountService.consensusLeave(NodeAddress, address, password);
        return isOk;
    }

    @ApiOperation(value = "查询所有非挖矿交易", notes = "查询所有地址下的所有非挖矿交易记录")
    @PostMapping(value = "searchTransactionsOutOfCoinBase")
    JSONObject searchTransactionsOutOfCoinBase(@RequestParam String address) {
        JSONObject resp = new JSONObject();
        try {
            resp.put("retCode", "0");
            JSONObject data = transferAccountService.searchAllTransfer(address);
            resp.put("data", data);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00014");
        }
        return resp;
    }


    @ApiOperation(value = "查询所有挖矿交易", notes = "查询所有地址下的所有挖矿交易记录")
    @PostMapping(value = "searchTransactionsOfCoinBase")
    JSONObject searchTransactionsOfCoinBase(@RequestParam String address, @RequestParam String date) {
        JSONObject resp = new JSONObject();
        try {
            resp.put("retCode", "0");
            JSONObject data = transferAccountService.searchAllCoinBaseTransfer(address, date);
            resp.put("data", data);
        } catch (Exception e) {
            resp.put("retCode", "1");
            resp.put("msgCode", "E00014");
        }
        return resp;
    }


    @ApiOperation(value = "查询当前账户的共识情况", notes = "查询当前账户的共识情况")
    @PostMapping(value = "searchAddressConsensusStatus")
    JSONObject searchAddressConsensusStatus(@RequestParam String address) {
        JSONObject resp = new JSONObject();
        try {
            if(address==null||address.equals("undefind")||address.equals("")){
                resp.put("retCode", "1");
                resp.put("msgCode", "E00015");
                return resp;
            }
            JSONObject data = transferAccountService.searchAddressConsensusStatus(address);
            if (data.size() == 0) {
                resp.put("retCode", "1");
                resp.put("msgCode", "E00016");
            } else {
                resp.put("retCode", "0");
                resp.put("data", data);
            }

        } catch (Exception e) {
            e.printStackTrace();
            resp.put("retCode", "1");
            resp.put("msgCode", "E00014");
        }
        return resp;
    }

}
