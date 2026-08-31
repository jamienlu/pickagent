package com.pickagent.w1d2;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示如何遍历异构输出并收集文本的 W1D2 学习辅助类。
 *
 * <p>类名与 DTO 结构保留当日练习状态，不作为生产提取器。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
public class ResponseTextExractor {
    /** 创建 W1D2 文本提取学习辅助类。 */
    public ResponseTextExractor() {
    }

    /**
     * 构造包含未知项和消息项的固定学习样例。
     *
     * @param item 未知输出项
     * @param msg1 第一段文本
     * @param msg2 第二段文本
     * @return 用于离线测试的响应包络
     */
    public ResponseEnvelope sample(UnknownItem item,OutputText msg1,OutputText msg2) {
        ResponseEnvelope responseEnvelope = new ResponseEnvelope();
        OutputItem outputItem1 = new OutputItem();
        outputItem1.setType("unknown");
        outputItem1.setMsgItem(item);
        OutputItem outputItem2 = new OutputItem();
        outputItem2.setType("message");
        MessageItem messageItem1 = new MessageItem();
        messageItem1.setRole("user");
        ContentItem contentItem1 = new ContentItem();
        contentItem1.setText(msg1);
        contentItem1.setType("text");
        ContentItem contentItem2 = new ContentItem();
        contentItem2.setType("text");
        contentItem2.setText(msg2);
        messageItem1.setOutput(new ContentItem[]{contentItem1, contentItem2});
        outputItem2.setMsgItem(messageItem1);
        responseEnvelope.setOutputItems(new OutputItem[]{outputItem1, outputItem2});
        Usage usage = new Usage();
        usage.setPrompt_tokens(10);
        usage.setCompletion_tokens(10);
        usage.setTotal_tokens(20);
        responseEnvelope.setUsage(usage);
        return responseEnvelope;
    }

    /**
     * 遍历响应中的 message 项并按顺序收集文本。
     *
     * @param responseEnvelope 学习型响应包络
     * @return 按输出顺序排列的文本列表
     */
    public List<String> mergeOutPutText(ResponseEnvelope responseEnvelope) {
        List<String> mergedText = new ArrayList<String>();
        for (int i = 0; i < responseEnvelope.getOutputItems().length; i++) {
            OutputItem outputItem = responseEnvelope.getOutputItems()[i];
            if (outputItem.getType().equals("message")) {
                MessageItem messageItem = outputItem.getMsgItem();
                for (ContentItem contentItem : messageItem.getOutput()) {
                    mergedText.add(contentItem.getText().getText());
                }
            }
        }
        return mergedText;
    }
}
