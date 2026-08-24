
import com.pickagent.w1d2.OutputText;
import com.pickagent.w1d2.ResponseEnvelope;
import com.pickagent.w1d2.ResponseTextExractor;
import com.pickagent.w1d2.UnknownItem;
import com.pickagent.w1d2.Usage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
/**
 * @author jamieLu
 * @create 2026-08-24
 */
public class ResponseTextExractorTest {
    private static ResponseEnvelope responseEnvelope;

    @BeforeAll
    public static void prepare() {
        UnknownItem item = new UnknownItem();
        item.setUnknownField("reasoning");
        OutputText outputText1 = new OutputText();
        outputText1.setText("this is output1");
        OutputText outputText2 = new OutputText();
        outputText2.setText("this is output2");
        responseEnvelope = new ResponseTextExractor().sample(item, outputText1, outputText2);
    }
    @Test
    public void mergeRight() {
        ResponseTextExractor responseTextExractor = new ResponseTextExractor();
        List<String> result = responseTextExractor.mergeOutPutText(responseEnvelope);
        assertEquals("this is output1", result.get(0));
        assertEquals("this is output2", result.get(1));
    }

    @Test
    public void useage() {
        Usage usage = responseEnvelope.getUsage();
        assertEquals(usage.getTotal_tokens(), usage.getPrompt_tokens() + usage.getCompletion_tokens());

    }
}
