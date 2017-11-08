package com.axa.ecmca.mail.pop3example;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.test.context.junit4.SpringRunner;

import javax.mail.Flags;
import javax.mail.MessagingException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@SpringBootTest
public class Pop3ExampleApplicationTests {

    @Autowired
    GreenMail greenMail;

    @Autowired
    Pop3MailReceiver pop3MailReceiver;

    @Autowired
    ServerSetup smtpServerSetup;

    @MockBean
    MessageHandler messageHandler;

    private void sendMail() {
        GreenMailUtil.sendTextEmail("user", "user@localhost",
                "some subject", "some body", smtpServerSetup); // --- Place your sending code here instead
    }

    private void purgeInbox() {
        Stream.of(greenMail.getReceivedMessages()).forEach(mimeMessage -> {
            try {
                mimeMessage.setFlag(Flags.Flag.DELETED, true);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void transactionCommit() throws Exception {

        // given
        purgeInbox();
        sendMail();
        assertThat(pop3MailReceiver.receive()).hasSize(1);

        // when
        Thread.sleep(500);

        // then
        verify(messageHandler).handleMessage(any(Message.class));
        assertThat(pop3MailReceiver.receive()).isEmpty();
    }

    @Test
    public void transactionRollback() throws Exception {

        // given
        doThrow(new RuntimeException("roll back transaction")).when(messageHandler).handleMessage(any(Message.class));
        purgeInbox();
        sendMail();
        assertThat(pop3MailReceiver.receive()).hasSize(1);

        // when
        Thread.sleep(500);

        // then
        assertThat(pop3MailReceiver.receive()).hasSize(1);
    }
}
