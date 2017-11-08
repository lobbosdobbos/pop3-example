package de.ikke.pop3example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.transaction.IntegrationResourceHolder;
import org.springframework.integration.transaction.TransactionSynchronizationProcessor;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
public class Pop3TransactionSynchronizationProcessor implements TransactionSynchronizationProcessor {

    @Override
    public void processBeforeCommit(IntegrationResourceHolder integrationResourceHolder) {
        log.debug("before commit");
    }

    @Override
    public void processAfterCommit(IntegrationResourceHolder integrationResourceHolder) {
        log.debug("after commit");
        if (integrationResourceHolder.getMessage() != null &&
                integrationResourceHolder.getMessage().getPayload() instanceof MimeMessage) {

            MimeMessage message = (MimeMessage) integrationResourceHolder.getMessage().getPayload();
            try {
                Folder folder = message.getFolder();
                folder.open(Folder.READ_WRITE);
                String messageId = message.getMessageID();
                Message[] messages = folder.getMessages();
                FetchProfile contentsProfile = new FetchProfile();
                contentsProfile.add(FetchProfile.Item.ENVELOPE);
                contentsProfile.add(FetchProfile.Item.CONTENT_INFO);
                contentsProfile.add(FetchProfile.Item.FLAGS);
                folder.fetch(messages, contentsProfile);

                Predicate<MimeMessage> messageIdIsEqual = msg -> {
                    try {
                        return msg.getMessageID().equals(messageId);
                    } catch (MessagingException e) {
                        throw new RuntimeException(e);
                    }
                };
                Consumer<MimeMessage> deleteMessage = msg -> {
                    try {
                        msg.setFlag(Flags.Flag.DELETED, true);
                    } catch (MessagingException e) {
                        log.error("Could not set DELETED flag on mime message", e);
                    }
                };

                Stream.of(messages)
                        .filter(msg -> msg instanceof MimeMessage)
                        .map(msg -> (MimeMessage) msg)
                        .filter(messageIdIsEqual)
                        .findFirst()
                        .ifPresent(deleteMessage);

                folder.close(true);
            } catch (Exception e) {
                log.error("Exception caught:", e);
            }
        }
    }

    @Override
    public void processAfterRollback(IntegrationResourceHolder integrationResourceHolder) {
        log.debug("afterRollback");
    }
}
