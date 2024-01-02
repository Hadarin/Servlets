/**
 * File         MailService.java
 *
 * Created      19.02.2007, 09:55:44
 * Last Update  15.03.2011, 11:12:05
 * --------------------------------------------------
 * History:
 * $Log:   P:/BankingSolutions/archives/MacocWebCOI/src/java/de/bnext/util/mail/MailService.java-arc  $
 *
 *    Rev 1.4   Apr 16 2008 14:23:40   tfreiwald
 * Check-in for final Alpha version
 *
 *    Rev 1.3   Apr 11 2008 17:05:44   tfreiwald
 * Chekc-in for final Alphaversion RC1
 *
 *    Rev 1.2   Apr 04 2008 18:23:54   tfreiwald
 * Check-in for Alpha Version
 *
 *    Rev 1.1   Mar 20 2008 19:03:02   tfreiwald
 * Daily check-in
 *
 *    Rev 1.0   Mar 10 2008 18:07:02   tfreiwald
 * Initial revision.
 *
 *    Rev 1.0   Mar 13 2007 16:41:54   tfreiwald
 * Initial revision.
 *
 */
package de.bnext.util.mail;

import de.bnext.persistence.om.torque.Employees;
import de.bnext.persistence.om.torque.EmployeesPeer;
import de.bnext.persistence.om.torque.PropertiesPeer;
import de.bnext.persistence.valueobject.RmAttachmentsVO;
import de.bnext.persistence.valueobject.coi.EmailVO;
import de.bnext.persistence.valueobject.coi.NotificationsVO;
import de.bnext.persistence.valueobject.coi.ResearchCheckRequestVO;
import de.bnext.util.Utils;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.*;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.stereotype.Component;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.torque.TorqueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * This class contains the functionality for sending e-mails <br>
 * <br>
 * Copyright:   Copyright (c) 2007 <br>
 * Company:     b-next Engineering GmbH <br>
 * Creator:     Thomas Freiwald <br>
 *
 * @author      $Author: dmitry.solonina $, $Date: 2023-09-26 16:45:14 +0200 (Tue, 26 Sep 2023) $
 * @version     $Revision: 7062 $
 */
@Component
public class MailService implements Serializable
{
    private static final Logger         LOG              = LoggerFactory.getLogger( MailService.class );

    public static final String          CONTENT_TYPE_TEXT  = addEncoding( "text/plain", "UTF-8");

    private static final long           serialVersionUID = 1L;

    private String                      smtpHost;

    private int                         smtpPort;

    private String                      mailDelimiter;

    private String                      mailFrom;

    private String                      username;

    private String                      password;

    private boolean                     requireAuthentication;
    
    private boolean                     debugEnabled;

    private Session                     session;
    
    @Autowired
    private MailSettingsLoader          settingsLoader;

    @PostConstruct
    public void init()
    {
        this.smtpHost = this.settingsLoader.getServerHost( "exsrv01" );
        this.smtpPort = this.settingsLoader.getServerPort( 25 );
        this.username = this.settingsLoader.getUsername( "" );
        this.password = this.settingsLoader.getPassword( "" );
        this.mailDelimiter = this.settingsLoader.getMailDelimiter( ";" );
        this.mailFrom = this.settingsLoader.getMailFrom( "cmc-compliance-portal@b-next.com" );
        this.requireAuthentication = this.settingsLoader.isAuthenticationRequired( false );
        this.debugEnabled = this.settingsLoader.isDebugEnabled( false );
    }

    /**
     * @return the mailDelimiter
     */
    public String getMailDelimiter()
    {
        return mailDelimiter;
    }

    /**
     * This method returns the session to the mail server, or creates one
     * if a session has not yet been established.
     *
     * @return Session - A Session to the mail server.
     */
    private synchronized Session getSession()
    {
        if ( this.session == null )
        {
            this.session = createSession();
            this.session.setDebugOut( new PrintStream( new MailLogStream( null ) ) );
            this.session.setDebug( true );
        }
        return ( this.session );
    }

    /**
     * This method encapsulates the authentication and properties creation needed
     * to establish a session with the mail server. The Session uses the
     * default provider implementation that comes with the JavaMail API from
     * SUN.
     *
     * @return Session - A session to the mail server.
     */
    private Session createSession()
    {
        Properties props = new Properties();
        // If authentication is required, property 'mail.smtp.auth' must be set to true. Otherwise, JavaMail
        // runtime won't send credentials to the SMTP server
        props.put( "mail.smtp.auth", String.valueOf( this.requireAuthentication ) );
        props.put( "mail.transport.protocol", "smtp" );
        props.put( "mail.debug", String.valueOf( this.debugEnabled) );
        props.put( "mail.mime.charset", "UTF-8" );
        
        return Session.getInstance( props );
    }

    /**
     * Sends the prepared e-mail
     *
     * @param emailVO - the e-mail value object
     * @throws MessagingException 
     * @throws AddressException 
     * @throws Exception
     */
    public void send( EmailVO emailVO ) throws AddressException, MessagingException
    {
        this.send( emailVO.getTo(), emailVO.getCc(), emailVO.getBcc(), emailVO.getSubject(), emailVO.getBody() );
    }

    /**
     * Sends the prepared e-mail
     *
     * @param to        - send-to-address
     * @param from      - from-address
     * @param subject   - the subject
     * @param body      - the e-mail body
     * @throws MessagingException 
     * @throws AddressException 
     * @throws Exception
     */
    public void send( String to, String subject, String body ) throws AddressException, MessagingException
    {
        this.send( to, "", "", subject, body );
    }

    /**
     * Sends the prepared e-mail
     *
     * @param to        - send-to-address
     * @param from      - from-address
     * @param cc        - cc-address
     * @param bcc       - bcc-address
     * @param subject   - the subject
     * @param body      - the e-mail body
     * @throws MessagingException 
     * @throws AddressException 
     * @throws Exception
     */
    public void send( String to, String cc, String bcc, String subject, String body ) throws AddressException, MessagingException
    {
        send( to, cc, bcc, subject, body, CONTENT_TYPE_TEXT );
    }

    public void send( String to, String cc, String bcc, String subject, String body, String contentType ) throws AddressException, MessagingException
    {

        try {
            final Message msg = new MimeMessage(getSession());

            msg.setFrom(new InternetAddress(this.mailFrom, this.mailFrom, "UTF-8"));
            setRecipients(to, null, cc, bcc, msg);
            msg.setContent(replaceUmlauts(body), contentType);
            msg.setHeader("X-Mailer", "JavaMailer");
            msg.setSentDate(new Date());
            msg.setSubject(MimeUtility.encodeText(StringEscapeUtils.escapeJava(subject), "UTF-8", "Q"));
            sendMessage(msg);
        } catch (Exception e) {
            LOG.error("Error email sending!!!");
        }
    }

    private String validateSubject(String subject) {
        // Remove or replace characters that are not allowed in an email subject
        String cleanedSubject = subject.replaceAll("[^\\p{L}\\p{N}\\s\\p{Punct}]", "");

        // Ensure the subject doesn't contain line breaks or special characters that could be exploited
        cleanedSubject = cleanedSubject.replaceAll("[\\r\\n]+", " ").trim();

        // Remove or replace characters that may be used for header injection
        cleanedSubject = cleanedSubject.replaceAll("[<>\"';]+", "");

        return cleanedSubject;
    }
    
    public void send( EmailVO email, ResearchCheckRequestVO request ) throws AddressException, MessagingException, IOException
    {
        final Message msg = new MimeMessage( getSession() );
        
        msg.setFrom( new InternetAddress( this.mailFrom ) );
        setRecipients( email.getTo(), email.getFrom(), email.getCc(), email.getBcc(), msg );
        msg.setSubject( email.getSubject() );

        Multipart content = new MimeMultipart();

        // email text
        BodyPart contentPart = new MimeBodyPart();
        contentPart.setText( email.getBody() );

        content.addBodyPart( contentPart );
        
        // email attachment
        if ( request.getRmAttachmentVO() != null || request.getRmAttachmentVO().getAttachmentAsStream() != null )
        {
            contentPart = new MimeBodyPart();
            String mediaType = getMediaTypeForAttachment( request.getRmAttachmentVO() ).toString();
            LOG.debug( "Detected media type :" + mediaType );
            DataSource source = new ByteArrayDataSource( request.getRmAttachmentVO().getAttachmentAsStream(), //
                                                         mediaType );
            contentPart.setDataHandler( new DataHandler( source ) );
            contentPart.setFileName( request.getRmAttachmentVO().getAttachmentName() );
            content.addBodyPart( contentPart );
        }

        // complete the email properties
        msg.setContent( content );


        msg.setHeader( "X-Mailer", "JavaMailer" );
        msg.setSentDate( new Date() );

        sendMessage( msg );
    }

    /**
     * @param request 
     * @return
     * @throws IOException
     */
    private static MediaType getMediaTypeForAttachment( RmAttachmentsVO attachment ) throws IOException
    {
        return getMediaTypeForFile( attachment.getAttachmentAsStream(), attachment.getAttachmentName() );
    }

    /**
     * Tries to guess the correct content type of the given file.
     * 
     * @param fileStream
     * @param fileName
     * @return
     * @throws IOException
     */
    public static MediaType getMediaTypeForFile( InputStream fileStream, String fileName ) throws IOException
    {
        TikaConfig config = TikaConfig.getDefaultConfig();
        Detector detector = config.getDetector();

        TikaInputStream stream = TikaInputStream.get( fileStream );

        Metadata metadata = new Metadata();
        metadata.add( Metadata.RESOURCE_NAME_KEY, fileName );
        MediaType mediaType = detector.detect( stream, metadata );

        return mediaType;
    }

    private void sendMessage( Message msg )
    {
        try
        {
            Transport transport = getSession().getTransport();
            transport.connect( this.smtpHost, this.smtpPort, this.username, this.password );
            transport.sendMessage( msg, msg.getAllRecipients() );
        }
        catch( AuthenticationFailedException e )
        {
            throw new MailException( "Authentication failed! Verify your username and password!" );
        }
        catch( MessagingException e )
        {
            throw new MailException( "Unable to send email!", e );
        }
    }

    /**
     * Sets the recipients
     *
     * @param to
     * @param replyTo
     * @param cc
     * @param bcc
     * @param msg
     * @throws AddressException
     * @throws MessagingException
     */
    private void setRecipients( String to, String replyTo, String cc, String bcc, Message msg ) throws AddressException, MessagingException
    {
        final String delimiter = this.mailDelimiter;

        /*
         * Set recipients for TO
         */
        String[] recipients = splitRecipients( to, delimiter );
        InternetAddress[] addressTo = new InternetAddress[recipients.length];

        for ( int i = 0; i < recipients.length; i++ )
        {
            addressTo[i] = new InternetAddress( recipients[i] );
            if ( MailService.LOG.isDebugEnabled() )
            {
                MailService.LOG.debug( "[MailService.setRecipients()] Using TO-address: " + ( i + 1 ) + ": " + addressTo[i] );
            }
        }

        msg.setRecipients( Message.RecipientType.TO, addressTo );

        /*
         * Set recipients for CC
         */
        if ( cc != null && cc.trim().length() > 0 )
        {
            recipients = splitRecipients( cc, delimiter );
            addressTo = new InternetAddress[recipients.length];

            for ( int i = 0; i < recipients.length; i++ )
            {
                addressTo[i] = new InternetAddress( recipients[i] );
                if ( MailService.LOG.isDebugEnabled() )
                {
                    MailService.LOG.debug( "[MailService.setRecipients()] Using CC-address: " + ( i + 1 ) + ": " + addressTo[i] );
                }
            }

            msg.setRecipients( Message.RecipientType.CC, addressTo );
        }

        /*
         * Set recipients for BCC
         */
        if ( bcc != null && bcc.trim().length() > 0 )
        {
            recipients = splitRecipients( bcc, delimiter );
            addressTo = new InternetAddress[recipients.length];

            for ( int i = 0; i < recipients.length; i++ )
            {
                addressTo[i] = new InternetAddress( recipients[i] );
                if ( MailService.LOG.isDebugEnabled() )
                {
                    MailService.LOG.debug( "[MailService.setRecipients()] Using BCC-address: " + ( i + 1 ) + ": " + addressTo[i] );
                }
            }

            msg.setRecipients( Message.RecipientType.BCC, addressTo );
        }

        /*
         * Set reply-to address
         */
        if ( replyTo != null && replyTo.trim().length() > 0 )
        {
            recipients = splitRecipients( replyTo, delimiter );
            addressTo = new InternetAddress[recipients.length];

            for ( int i = 0; i < recipients.length; i++ )
            {
                addressTo[i] = new InternetAddress( recipients[i] );
                if ( MailService.LOG.isDebugEnabled() )
                {
                    MailService.LOG.debug( "[MailService.setRecipients()] Using reply-to-address: " + ( i + 1 ) + ": " + addressTo[i] );
                }
            }

            msg.setReplyTo( addressTo );
        }
    }

    /**
     * Splits recipient out of recipient string
     *
     * @param recipientsList - string containing addresses
     * @param delimiter      - the address delimiter
     * @return String[]      - string array containing each splitted e-mail address
     */
    private String[] splitRecipients( String recipientsList, String delimiter )
    {
        final StringTokenizer st = new StringTokenizer( recipientsList, delimiter );
        String[] recipients;
        final int tokencount = st.countTokens();

        if ( tokencount > 0 )
        {
            recipients = new String[tokencount];

            for ( int i = 0; i < recipients.length; i++ )
            {
                recipients[i] = st.nextToken();
            }
        }
        else
        {
            recipients = new String[1];
            recipients[0] = recipientsList;
        }

        return recipients;
    }

    /**
     * Method will lookup recipients email from employee table and will set email subject and text according to notification.
     * @param notif Notification to be used as source of data.
     * @throws Exception if input is null or recipient id is null or if employee with given id is not in database or if he has no valid email.
     */
    public void send( NotificationsVO notif ) throws Exception
    {
        if ( null == notif || null == notif.getRecipientEmployeeId() )
        {
            throw new Exception( "Null on input." );
        }
        Employees emp = EmployeesPeer.findEmployeeByPrimaryKey( notif.getRecipientEmployeeId() );
        if ( null == emp )
        {
            throw new TorqueException( "Unable to find employee." );
        }
        if ( StringUtils.isEmpty( emp.getEmail() ) )
        {
            throw new Exception( "Invalid employee email." );
        }
        send( emp.getEmail(), notif.getNotificationSubject(), notif.getNotificationText() );
    }

    private static String addEncoding( String contentType, String encodingString )
    {
        try
        {
            Charset encoding = Charset.forName( encodingString );

            contentType += "; charset=" + encoding.name();
        }
        catch( Exception e )
        {
            LOG.error( "The encoding [" + encodingString + "] is unknown. The e-mail service will use the default encoding. ", e );
            return contentType;
        }
        return contentType;
    }

    private static String replaceUmlauts( String text )
    {
        return isReplaceUmlauts() ? Utils.replaceUmlauts( text ) : text;
    }

    public static boolean isReplaceUmlauts() {
        try {
            return BooleanUtils.toBoolean(PropertiesPeer.findSystemPropertyByKey(
                    "de.bnext.compliance.Mail.ReplaceUmlauts",
                    "false").getPropertyValue());
        } catch (Exception e) {
            LOG.warn("ReplaceUmlauts property not found!");
        }
        return false;
    }
}

