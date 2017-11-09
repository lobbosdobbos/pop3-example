package de.ikke.pop3example;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.transaction.DefaultTransactionSynchronizationFactory;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.integration.transaction.TransactionSynchronizationFactory;
import org.springframework.messaging.MessageHandler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class Pop3ExampleApplication {

    @Value("${mailserver.username}")
    String userName;

    @Value("${mailserver.password}")
    String password;

    @Value("${mailserver.pop3.port}")
    int pop3Port;

    @Value("${mailserver.smtp.port}")
    int smtpPort;

    public static void main(String[] args) {
        SpringApplication.run(Pop3ExampleApplication.class, args);
    }

    @Bean
    public PlatformTransactionManager platformTransactionManager() {
        return new PseudoTransactionManager();
    }

    @Bean
    public TransactionSynchronizationFactory transactionSynchronizationFactory() {
        return new DefaultTransactionSynchronizationFactory(new Pop3TransactionSynchronizationProcessor());
    }

    @Bean
    public ServerSetup smtpServerSetup() {
        return new ServerSetup(smtpPort, "localhost", ServerSetup.PROTOCOL_SMTP);
    }

    @Bean
    public GreenMail greenMail() {
        ServerSetup pop3ServerSetup = new ServerSetup(pop3Port, "localhost", ServerSetup.PROTOCOL_POP3);
        pop3ServerSetup.setServerStartupTimeout(5000);
        GreenMail greenMail = new GreenMail(new ServerSetup[]{pop3ServerSetup, smtpServerSetup()});
        greenMail.start();
        greenMail.setUser(userName, password);
        return greenMail;
    }

    @Bean
    public Pop3MailReceiver pop3MailReceiver() {
        return new Pop3MailReceiver("localhost", pop3Port, userName, password);
    }

    @Bean
    public MailReceivingMessageSource mailReceivingMessageSource() {
        return new MailReceivingMessageSource(pop3MailReceiver());
    }

    @Bean
    public IntegrationFlow integrationFlow() {
        return IntegrationFlows.from(mailReceivingMessageSource(),
                c -> c.poller(Pollers
                        .fixedRate(10)
                        .transactional()
                        .transactionSynchronizationFactory(transactionSynchronizationFactory())
                        .maxMessagesPerPoll(1)))
                .handle(messageHandler())
                .get();
    }

    @Bean
    public MessageHandler messageHandler() {
        return System.out::println;
    }

}
