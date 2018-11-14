/*
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
package org.talust.core.transaction;

import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.talust.common.crypto.Utils;
import org.talust.common.crypto.VarInt;
import org.talust.common.exception.ProtocolException;
import org.talust.core.core.NetworkParams;
import org.talust.core.model.Message;
import org.talust.core.script.Script;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易输入，本次的输入是上次的输出
 */
@Document(collection = "transactionInput")
public class TransactionInput extends Message implements Input {

	public static final long NO_SEQUENCE = 0xFFFFFFFFL;

	@Transient
	private Transaction parent;
	//上次的输出
	private List<TransactionOutput> froms;

	@Transient
	private long sequence;
	@Transient
	private byte[] scriptBytes;
	@Transient
	private Script scriptSig;

	public TransactionInput() {
	}
	
	public TransactionInput(TransactionOutput from) {
		super();
		this.froms = new ArrayList<TransactionOutput>();
		this.froms.add(from);

		parent = from.getParent();
		
        this.sequence = NO_SEQUENCE;
	}
	
	public TransactionInput(NetworkParams network, Transaction transaction, byte[] payload, int offset) {
		super(network, payload, offset);
		this.parent = transaction;
	}

	/**
	 * 序列化交易输入
	 * @param stream
	 * @throws IOException 
	 */
	public void serialize(OutputStream stream) throws IOException {
		//上一交易的引用
		if(froms == null || froms.size() == 0) {
			stream.write(new VarInt(0).encode());
		} else {
	        stream.write(new VarInt(froms.size()).encode());
	        for (TransactionOutput from : froms) {
	        	stream.write(from.getParent().getHash().getReversedBytes());
	        	Utils.uint32ToByteStreamLE(from.getIndex(), stream);
			}
		}
		//签名的长度
        stream.write(new VarInt(scriptBytes.length).encode());
        //签名
        stream.write(scriptBytes);
        //sequence，送者定义的交易版本，用于在交易被写入block之前更改交易
        Utils.uint32ToByteStreamLE(sequence, stream);
	}
	

	/**
	 * 反序列化交易的输入部分
	 */
	@Override
	protected void parse() throws ProtocolException {

        froms = new ArrayList<TransactionOutput>();
        
        int fromSize = (int)readVarInt();
        
        for (int i = 0; i < fromSize; i++) {
        	TransactionOutput pre = new TransactionOutput();
        	Transaction t = new Transaction(network);
        	t.setHash(readHash());
        	pre.setParent(t);
        	pre.setIndex((int)readUint32());
        	froms.add(pre);
		}
    
        //输入签名的长度
        int signLength = (int)readVarInt();
        scriptBytes = readBytes(signLength);
        scriptSig = new Script(scriptBytes);
        sequence = readUint32();
        
        length = cursor - offset;
	}
	
	/**
	 * 清空输入脚本的签名，用在私匙签名之前
	 */
	public void clearScriptBytes() {
        scriptBytes = new byte[0];
        scriptSig = null;
    }
	
	public boolean addFrom(TransactionOutput from) {
		if(froms == null) {
			froms = new ArrayList<TransactionOutput>();
		}
		return froms.add(from);
	}
	
	public List<TransactionOutput> getFroms() {
		return froms;
	}
	public void setFroms(List<TransactionOutput> froms) {
		this.froms = froms;
	}
	public Transaction getParent() {
		return parent;
	}
	public void setParent(Transaction parent) {
		this.parent = parent;
	}
	public boolean hasSequence() {
        return sequence > 0;
    }
	public void setSequence(long sequence) {
		this.sequence = sequence;
	}
	public long getSequence() {
		return sequence;
	}
	public byte[] getScriptBytes() {
		if(scriptBytes == null && scriptSig != null) {
			scriptBytes = scriptSig.getProgram();
		}
		return scriptBytes;
	}

	public void setScriptBytes(byte[] scriptBytes) {
		this.scriptBytes = scriptBytes;
		this.scriptSig = new Script(scriptBytes);
	}

	public Script getScriptSig() {
		if(scriptSig == null && scriptBytes != null) {
			scriptSig = new Script(scriptBytes);
		}
		return scriptSig;
	}

	public void setScriptSig(Script scriptSig) {
		this.scriptSig = scriptSig;
		this.scriptBytes = scriptSig.getProgram();
	}

	@Override
	public Script getFromScriptSig() {
		if(froms == null || froms.size() == 0) {
			return null;
		}
		return froms.get(0).getScript();
	}

	@Override
	public String toString() {
		return "TransactionInput [from=" + froms + ", sequence=" + sequence + ", scriptSig=" + scriptSig + "]";
	}

}
