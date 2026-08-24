package com.pickagent.w1d2;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamieLu
 * @create 2026-08-24
 */
public class ResponseTextExractor {

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
