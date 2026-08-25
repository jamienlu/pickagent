import com.pickagent.w1d3.ResponseRequestFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jamieLu
 * @create 2026-08-25
 */
public class ResponseRequestFactoryTest {
    @Test
    public void normal() {
        ResponseRequestFactory responseRequestFactory = ResponseRequestFactory.builder()
                .model("gpt-3.5-turbo")
                .input("this is test")
                .instructions("You are a helpful assistant.")
                .maxOutputTokens(1024)
                .store(false).build();
        assertNotNull(responseRequestFactory.builderResponseParams());
    }
    @Test
    public void prompt() {
        ResponseRequestFactory responseRequestFactory = ResponseRequestFactory.builder()
                .model("gpt-3.5-turbo")
                .input("")
                .instructions("You are a helpful assistant.")
                .maxOutputTokens(1024)
                .store(false).build();
        assertThrows(IllegalArgumentException.class, responseRequestFactory::builderResponseParams);
    }
    @Test
    public void token() {
        ResponseRequestFactory responseRequestFactory = ResponseRequestFactory.builder()
                .model("gpt-3.5-turbo")
                .input("this is test")
                .instructions("You are a helpful assistant.")
                .maxOutputTokens(-2)
                .store(false).build();
        assertThrows(IllegalArgumentException.class, responseRequestFactory::builderResponseParams);
    }
}
