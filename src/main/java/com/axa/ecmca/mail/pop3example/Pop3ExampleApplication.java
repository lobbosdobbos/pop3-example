package com.axa.ecmca.mail.pop3example;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.PollerSpec;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.integration.transaction.DefaultTransactionSynchronizationFactory;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.integration.transaction.TransactionSynchronizationFactory;
import org.springframework.messaging.MessageHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class Pop3ExampleApplication {

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
        return new ServerSetup(10025, "localhost", ServerSetup.PROTOCOL_SMTP);
    }

    @Bean
    public GreenMail greenMail() {
        ServerSetup pop3Config = new ServerSetup(10110, "localhost", ServerSetup.PROTOCOL_POP3);
        pop3Config.setServerStartupTimeout(5000);
        GreenMail greenMail = new GreenMail(new ServerSetup[]{pop3Config, smtpServerSetup()});
        greenMail.start();
        greenMail.setUser("user", "password");
        return greenMail;
    }

    @Bean
    public Pop3MailReceiver pop3MailReceiver() {
        return new Pop3MailReceiver("pop3://user:password@localhost:10110/INBOX");
    }

    @Bean
    MailReceivingMessageSource mailReceivingMessageSource() {
        return new MailReceivingMessageSource(pop3MailReceiver());
    }

    @Bean
    public IntegrationFlow integrationFlow() {
        return IntegrationFlows.from(mailReceivingMessageSource(),
                c -> {
                    PollerSpec pollerSpec = Pollers.fixedRate(100);
                    pollerSpec.transactional();
                    pollerSpec.transactionSynchronizationFactory(transactionSynchronizationFactory());
                    c.poller(pollerSpec.maxMessagesPerPoll(1));
                })
                .handle(messageHandler())
                .get();
    }

    @Bean
    public MessageHandler messageHandler() {
        return System.out::println;

    }
}
