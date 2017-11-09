package de.ikke.pop3example;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.test.context.junit4.SpringRunner;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class Pop3ExampleApplicationTests {

    @Value("${mailserver.username}")
    String userName;

    @Autowired
    GreenMail greenMail;

    @Autowired
    Pop3MailReceiver pop3MailReceiver;

    @Autowired
    ServerSetup smtpServerSetup;

    @Autowired
    SourcePollingChannelAdapter sourcePollingChannelAdapter;

    @Autowired
    ApplicationContext ctx;

    @MockBean
    MessageHandler messageHandler;

    @Before
    public void setUp() throws Exception {
        stopPolling();
        purgeInbox();
    }

    @Test
    public void transactionCommit() throws Exception {

        // given
        sendMail();
        assertThat(messagesInMailbox()).hasSize(1);

        // when
        pollForMilliseconds(50);

        // then
        verify(messageHandler).handleMessage(any(Message.class));
        assertThat(messagesInMailbox()).isEmpty();
    }

    @Test
    public void transactionRollback() throws Exception {

        // given
        doThrow(new RuntimeException("roll back transaction")).when(messageHandler).handleMessage(any(Message.class));
        sendMail();
        assertThat(messagesInMailbox()).hasSize(1);

        // when
        pollForMilliseconds(50);

        // then
        verify(messageHandler, atLeast(2)).handleMessage(any(Message.class));
        assertThat(messagesInMailbox()).hasSize(1);
    }

    private Object[] messagesInMailbox() throws MessagingException {
        return pop3MailReceiver.receive();
    }

    @Test
    public void transactionRollbackAndCommit() throws Exception {

        // given
        doThrow(new RuntimeException()).when(messageHandler).handleMessage(argThat(item -> {
            try {
                return ((MimeMessage) ((Message) item).getPayload()).getContent().toString().startsWith("exception");
            } catch (Exception e) {
                return false;
            }
        }));
        sendMail("exception");
        sendMail();
        sendMail();
        assertThat(messagesInMailbox()).hasSize(3);

        // when
        pollForMilliseconds(50);

        // then
        verify(messageHandler, atLeast(3)).handleMessage(any(Message.class));
        assertThat(messagesInMailbox()).hasSize(1);
    }

    private void sendMail(String body) {
        GreenMailUtil.sendTextEmail(userName, "someotheruser@localhost",
                "some subject", body, smtpServerSetup); // --- Place your sending code here instead
    }

    private void sendMail() {
        sendMail("some body");
    }

    private void purgeInbox() throws MessagingException {
        for (MimeMessage mimeMessage : greenMail.getReceivedMessages()) {
            mimeMessage.setFlag(Flags.Flag.DELETED, true);
        }
    }

    private void startPolling() {
        sourcePollingChannelAdapter.start();
    }

    private void stopPolling() {
        sourcePollingChannelAdapter.stop();
    }

    private void pollForMilliseconds(long millis) throws InterruptedException {
        startPolling();
        sleep(millis);
        stopPolling();
    }

}
