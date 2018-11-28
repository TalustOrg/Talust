package org.talust.consensus;

import lombok.extern.slf4j.Slf4j;
import org.talust.common.model.Message;
import org.talust.common.model.MessageChannel;
import org.talust.common.model.MessageType;
import org.talust.common.model.SuperNode;
import org.talust.common.tools.CacheManager;
import org.talust.common.tools.ThreadPool;
import org.talust.network.model.MyChannel;
import org.talust.network.netty.ChannelContain;
import org.talust.network.netty.ConnectionManager;
import org.talust.network.netty.SynRequest;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j //进入共识的会议,里面含有各个会议成员
public class Conference {
    private static Conference instance = new Conference();

    private Conference() {
    }

    public static Conference get() {
        return instance;
    }

    //当前的master
    private SuperNode master;
    //构造一个线程池
    private ThreadPoolExecutor threadPool = ThreadPool.get().threadPool;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private int voteState = VoteStatus.NOT_NEED.getType();

    /**
     * 获取当前的master节点
     *
     * @return
     */
    public SuperNode reqNetMaster() {
        try {
            Collection<MyChannel> superChannels = ChannelContain.get().getSuperChannels();
            int superSize = superChannels.size();
            if (superSize > 0) {//说明当前超级节点网络有多个
                int needOkNumber = superSize / 2;//需要回应的数量
                //向每一个超级节点请求加入结果
                log.info("当前节点ip:{} 向各个超级节点请求当前master节点,其他超级节点数量为:{}", ConnectionManager.get().getSelfIp(), superSize);
                List<Future<String>> results = new ArrayList<>();
                for (final MyChannel superChannel : superChannels) {
                    Future<String> submit = threadPool.submit(() -> {
                        Message nodeMessage = new Message();
                        nodeMessage.setType(MessageType.MASTER_REQ.getType());//master请求
                        MessageChannel nm = SynRequest.get().synReq(nodeMessage, superChannel.getRemoteIp());
                        if (nm != null) {
                            byte[] content = nm.getMessage().getContent();
                            if (content != null) {//ip
                                log.info("reqNetMaster 节点返回master 节点IP为：{},来源于", new String(content), nm.getFromIp());
                                return new String(content);
                            }
                        }
                        return null;
                    });
                    results.add(submit);
                }

                Map<String, Integer> rc = new HashMap<>();
                while (true) {
                    boolean isOk = false;
                    for (Future<String> result : results) {
                        try {
                            boolean done = result.isDone();
                            if (done) {
                                String conferenceMemeber = result.get();
                                Integer number = rc.get(conferenceMemeber);
                                if (number == null) {
                                    number = 0;
                                }
                                number++;
                                rc.put(conferenceMemeber, number);
                                if (number > needOkNumber) {
                                    isOk = true;
                                }
                                results.remove(result);
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (isOk) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Iterator<Map.Entry<String, Integer>> it = rc.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Integer> next = it.next();
                    Integer value = next.getValue();
                    if ("NO_MASTER".equals(next.getKey())) {
                        needOkNumber = (superSize - value) / 2;
                        log.info("NO_MASTER 数据数量为：{},需要确认的节点IP数量为:{}", value, needOkNumber);
                        continue;
                    }
                    log.info("数据数量为：{},需要确认的节点IP数量为:{}", value, needOkNumber);
                    if (value > needOkNumber) {
                        log.info("reqNetMaster 认定节点IP为：{}", next.getKey());
                        master = ConnectionManager.get().getSuperNodeByIp(next.getKey());
                        log.info("reqNetMaster 当前出块节点IP 为：{}", master.getIp());
                        ConnectionManager.get().setMasterIp(master.getIp());
                        CacheManager.get().setCurrentBlockGenIp(master.getIp());
                        if (master.getIp().equals(ConnectionManager.get().getSelfIp())) {//如果是自己,则开始生成块
                            ConsensusService.get().startGenBlock();
                        } else {
                            ConsensusService.get().stopGenBlock();
                        }
                    }
                }
            } else {
                boolean superNode = ConnectionManager.get().superNode;
                if (superNode) {//如果当前节点是超级节点,则启动共识机制
                    master = ConnectionManager.get().getSuperNodeByIp(ConnectionManager.get().getSelfIp());
                    log.info("reqNetMaster 当前出块节点IP 为：{}", master.getIp());
                    ConnectionManager.get().setMasterIp(master.getIp());
                    CacheManager.get().setCurrentBlockGenIp(master.getIp());
                    if (master.getIp().equals(ConnectionManager.get().getSelfIp())) {//如果是自己,则开始生成块
                        ConsensusService.get().startGenBlock();
                    } else {
                        ConsensusService.get().stopGenBlock();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("认定后的出块节点是否已经出现，若未出现3S后循环继续。");
        return master;
    }

    /**
     * 改变当前master节点
     */
    public void changeMaster() {
        log.info("变更master节点启动！");
        lock.writeLock().lock();
        try {
            String currentBlockGenIp = CacheManager.get().getCurrentBlockGenIp();
            if (null != master) {
                if (this.master.getIp().equals(currentBlockGenIp)) {//说明需要改变master节点
                    Collection<SuperNode> superNodes = ConnectionManager.get().getSuperNodes();
                    List<SuperNode> sns = new ArrayList<>();
                    for (SuperNode superNode : superNodes) {
                        sns.add(superNode);
                    }
                    Collections.sort(sns, Comparator.comparing(SuperNode::getIp));

                    boolean selOk = false;
                    SuperNode nextMaster = sns.get(0);
                    for (SuperNode superNode : sns) {
                        if (selOk) {
                            nextMaster = superNode;
                            break;
                        }
                        if (superNode.getIp().equals(currentBlockGenIp)) {
                            selOk = true;
                        }
                    }
                    this.master = nextMaster;
                    log.info("changeMaster  改变当前master节点,当前出块节点IP 为：{}", master.getIp());
                    ConnectionManager.get().setMasterIp(master.getIp());
                    if (master.getIp().equals(ConnectionManager.get().getSelfIp())) {//如果是自己,则开始生成块
                        ConsensusService.get().startGenBlock();
                    } else {
                        ConsensusService.get().stopGenBlock();
                    }
                }
            } else {
                //说明暂时无法更新master节点,需要强制更新自身缓存
                reqNetMaster();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 校验新的masterip是否合法
     *
     * @param newMasterIp
     * @return
     */
    public boolean checkNewMaster(String newMasterIp) {
        if (newMasterIp.equals(master)) {//说明当前节点已经成功切换了,所以直接验证其ok
            return true;
        }
        boolean haveIp = false;//用于判断要切换的ip是否在本地存储有
        Collection<MyChannel> superChannels = ChannelContain.get().getSuperChannels();
        for (MyChannel superChannel : superChannels) {
            String remoteIp = superChannel.getRemoteIp();
            if (remoteIp.equals(newMasterIp)) {
                haveIp = true;
                break;
            }
        }
        if (haveIp) {
            if (voteState == VoteStatus.LOOKING.getType()) {//当前节点也在更新master节点
                String currentBlockGenIp = CacheManager.get().getCurrentBlockGenIp();
                Collection<SuperNode> superNodes = ConnectionManager.get().getSuperNodes();
                List<SuperNode> sns = new ArrayList<>();
                for (SuperNode superNode : superNodes) {
                    sns.add(superNode);
                }
                Collections.sort(sns, Comparator.comparing(SuperNode::getIp));

                boolean selOk = false;
                SuperNode nextMaster = sns.get(0);
                for (SuperNode superNode : sns) {
                    if (selOk) {
                        nextMaster = superNode;
                        break;
                    }
                    if (superNode.getIp().equals(currentBlockGenIp)) {
                        selOk = true;
                    }
                }
                if (nextMaster.equals(newMasterIp)) {//说明当前节点认同此更新
                    log.info("checkNewMaster 改变当前master节点,当前出块节点IP 为：{}", master.getIp());
                    ConnectionManager.get().setMasterIp(newMasterIp);
                    if (master.getIp().equals(ConnectionManager.get().getSelfIp())) {//如果是自己,则开始生成块
                        ConsensusService.get().startGenBlock();
                    } else {
                        ConsensusService.get().stopGenBlock();
                    }
                    return true;
                }
            } else {
                changeMaster();
                if (newMasterIp.equals(master)) {
                    log.info("checkNewMaster 改变当前master节点,当前出块节点IP 为：{}", master.getIp());
                    ConnectionManager.get().setMasterIp(newMasterIp);
                    if (master.getIp().equals(ConnectionManager.get().getSelfIp())) {//如果是自己,则开始生成块
                        ConsensusService.get().startGenBlock();
                    } else {
                        ConsensusService.get().stopGenBlock();
                    }
                    return true;
                }
            }
        }
        return false;
    }


    public SuperNode getMaster() {
        return master;
    }

}
