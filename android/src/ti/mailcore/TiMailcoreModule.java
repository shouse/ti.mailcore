/**
 * TiMailcore
 */

package ti.mailcore;

import org.appcelerator.titanium.TiApplication;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.kroll.common.Log;

import com.libmailcore.MailException;
import com.libmailcore.OperationCallback;
import com.libmailcore.ConnectionType;
import com.libmailcore.IMAPOperation;
import com.libmailcore.IMAPSession;
import com.libmailcore.IMAPMessage;
import com.libmailcore.IMAPFetchContentOperation;
import com.libmailcore.IMAPFetchMessagesOperation;
import com.libmailcore.IMAPFetchFoldersOperation;
import com.libmailcore.IMAPFolderInfoOperation;
import com.libmailcore.IMAPMessagesRequestKind;
import com.libmailcore.IMAPFolder;
import com.libmailcore.SMTPSession;
import com.libmailcore.SMTPOperation;
import com.libmailcore.OperationCallback;
import com.libmailcore.IndexSet;
import com.libmailcore.Range;
import com.libmailcore.Address;
import com.libmailcore.AbstractMessage;
import com.libmailcore.MessageHeader;
import com.libmailcore.MessageParser;
import com.libmailcore.MessageBuilder;
import com.libmailcore.Attachment;
import com.libmailcore.AbstractPart;

import java.util.HashMap;
import java.util.ArrayList;

import android.util.Base64;

@Kroll.module(name="TiMailcore", id="ti.mailcore")
public class TiMailcoreModule extends KrollModule {
	private static final String LCAT = "TiMailcoreModule";
	private static final boolean DBG = TiConfig.LOGD;

	public TiMailcoreModule() {
		super();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app) {}

	private Object _applyCredentials(KrollDict credentials, boolean smpt) {
		String email = (String)credentials.get("email");
		String password = (String)credentials.get("password");
		String host_name = (String)credentials.get("host");
		int port_name = credentials.getInt("port");
		int ctype = credentials.containsKey("ctype") ? credentials.getInt("ctype") : ConnectionType.ConnectionTypeTLS;
		String oauth_token = (credentials.get("oauth_token") != null) ? (String)credentials.get("oauth_token") : null;

		Object session;

		if(smpt) {
			SMTPSession s_session = new SMTPSession();
			s_session.setUsername(email);
			s_session.setPassword(password);
			s_session.setHostname(host_name);
			s_session.setPort(port_name);
			s_session.setConnectionType(ctype);
			if(oauth_token != null) {
				s_session.setOAuth2Token(oauth_token);
			}
			session = s_session;
		} else {
			IMAPSession i_session = new IMAPSession();
			i_session.setUsername(email);
			i_session.setPassword(password);
			i_session.setHostname(host_name);
			i_session.setPort(port_name);
			i_session.setConnectionType(ctype);
			if(oauth_token != null) {
				i_session.setOAuth2Token(oauth_token);
			}
			session = i_session;
		}
		return session;
	}

	private KrollDict _messageToJSON(AbstractMessage msg) {
		KrollDict email = compose();
		KrollDict email_headers = email.getKrollDict("headers");
		KrollDict email_addresses = email.getKrollDict("addresses");
		MessageHeader header = msg.header();

		if(header != null) {
			// Basic data and headers
			if(header.subject() != null) {
				email.put("subject", header.subject());
			}
			if(header.date() != null) {
				email.put("date", header.date().toString());
			}
			if(header.receivedDate() != null) {
				email.put("receivedDate", header.receivedDate().toString());
			}
			if(header.allExtraHeadersNames() != null) {
				for(String extra : header.allExtraHeadersNames()) {
					String extra_data = header.extraHeaderValueForName(extra);
					if(extra_data != null) {
						email_headers.put(extra, extra_data);
					}
				}
			}

			// 'From' address
			if(header.from() != null) {
				KrollDict from = email_addresses.getKrollDict("from");
				String display_name = header.from().displayName();
				String mailbox = header.from().mailbox();
				if(display_name != null) {
					from.put("name", display_name);
				}
				if(mailbox != null) {
					from.put("mailbox", mailbox);
				}
			}

			// Remainder of the address types
			KrollDict address_sections = new KrollDict();
			address_sections.put("to", header.to());
			address_sections.put("cc", header.cc());
			address_sections.put("bcc", header.bcc());
			address_sections.put("replyTo", header.replyTo());
			for(Object address_section_obj : address_sections.keySet()) {
				String address_section = (String)address_section_obj;
				java.util.List<Address> addresses = (java.util.List<Address>)(address_sections.get(address_section));

				if(addresses != null) {
					ArrayList my_addresses = new ArrayList();

					for(Address address : addresses) {
						KrollDict new_address = new KrollDict();

						String display_name = address.displayName();
						String mailbox = address.mailbox();
						if(display_name != null) {
							new_address.put("name", display_name);
						}
						if(mailbox != null) {
							new_address.put("mailbox", mailbox);
						}
						my_addresses.add(new_address);
					}

					email_addresses.put(address_section, my_addresses.toArray(new Object[my_addresses.size()]));
				}
			}
		}

		return email;
	}

	// Private methods defining email json structure
	private KrollDict _getEmailStructure() {
		KrollDict structure = new KrollDict();
		structure.put("subject", "");
		structure.put("body", "");
		return structure;
	}
	private KrollDict _getHeaderStructure() {
		KrollDict structure = new KrollDict();
		return structure;
	}
	private KrollDict _getAddressStructure() {
		KrollDict structure = new KrollDict();
		structure.put("to", new Object[0]);
		structure.put("cc", new Object[0]);
		structure.put("bcc", new Object[0]);
		structure.put("from", new KrollDict());
		structure.put("replyTo", new Object[0]);
		return structure;
	}

	private void _applyHeader(KrollDict header, KrollDict email) {
		email.put("headers", header);
	}

	private void _applyAddress(KrollDict address, KrollDict email) {
		email.put("addresses", address);
	}

	// Abstract base class of all callback job handlers. Just have to handle
	// creation of the operation and formatting of its results.
	private abstract class CallbackCaller<O, S> implements OperationCallback {
		private O operation;
		protected KrollFunction callback;
		private S session;

		public CallbackCaller(KrollDict credentials, KrollFunction cb, boolean smpt) {
			callback = cb;
			session = (S)(_applyCredentials(credentials, smpt));
		}

		abstract protected void operationStart(O op); // Because java is stupid and cant call methods on generics
		abstract protected O createOperation(S session);
		abstract protected Object formatResult(O operation);

		public void start() {
			operation = createOperation(session);
			operationStart(operation);
		}

		public void succeeded() {
			callback.call(getKrollObject(), new Object[]{null, formatResult(operation)});
		}

		public void failed(MailException exception) {
			callback.call(getKrollObject(), new Object[]{exception.getMessage(), null});
		}
	}

	// IMAP and SMTP specific versions
	private abstract class CallbackCallerIMAP extends CallbackCaller<IMAPOperation, IMAPSession> {
			public CallbackCallerIMAP(KrollDict credentials, KrollFunction cb, boolean smpt) {
				super(credentials, cb, false);
			}

			protected void operationStart(IMAPOperation op) {
				op.start(this);
			}
	}
	private abstract class CallbackCallerSMTP extends CallbackCaller<SMTPOperation, SMTPSession> {
			public CallbackCallerSMTP(KrollDict credentials, KrollFunction cb, boolean smpt) {
				super(credentials, cb, true);
			}

			protected void operationStart(SMTPOperation op) {
				op.start(this);
			}
	}

	// Get list of folders
	@Kroll.method
	public void getFolders(KrollDict imap, KrollFunction cb) {
		CallbackCaller caller = new GetFoldersCaller(imap, cb);
		caller.start();
	}
	private class GetFoldersCaller extends CallbackCallerIMAP {
		public GetFoldersCaller(KrollDict credentials, KrollFunction cb) {
			super(credentials, cb, false);
		}

		protected IMAPOperation createOperation(IMAPSession session) {
			return session.fetchAllFoldersOperation();
		}

		protected Object formatResult(IMAPOperation operation) {
			java.util.List<IMAPFolder> folders = ((IMAPFetchFoldersOperation)operation).folders();
			ArrayList result = new ArrayList();
			for(IMAPFolder folder : folders) {
				result.add(folder.path());
			}
			return result.toArray(new String[result.size()]);
		}
	}


	// Get Info for a specific folder
	@Kroll.method
	public void getFolderInfo(KrollDict imap, String folder, KrollFunction cb) {
		CallbackCaller caller = new GetFolderInfoCaller(imap, folder, cb);
		caller.start();
	}
	private class GetFolderInfoCaller extends CallbackCallerIMAP {
		private String folder;

		public GetFolderInfoCaller(KrollDict credentials, String f, KrollFunction cb) {
			super(credentials, cb, false);
			folder = f;
		}

		protected IMAPOperation createOperation(IMAPSession session) {
			return session.folderInfoOperation(folder);
		}

		protected Object formatResult(IMAPOperation operation) {
			IMAPFolderInfoOperation info_op = (IMAPFolderInfoOperation)operation;

			KrollDict result = new KrollDict();
			result.put("UIDNEXT", info_op.info().uidNext());
			result.put("UIDVALIDITY", info_op.info().uidValidity());
			result.put("HIGHESTMODSEQ", info_op.info().modSequenceValue());
			result.put("messages_count", info_op.info().messageCount());

			return result;
		}
	}

	// Get basic info of a mail folder with optional uid range
	@Kroll.method
	public void getMail(KrollDict imap, String folder, long range[], KrollFunction cb) {
		if(range == null) {
			range = new long[]{1, Long.MAX_VALUE};
		}
		IndexSet uids = IndexSet.indexSetWithRange(new Range(range[0], range[1] - range[0]));
		CallbackCaller caller = new GetMailCaller(imap, cb, folder, uids);
		caller.start();
	}
	private class GetMailCaller extends CallbackCallerIMAP {
		private String folder;
		private IndexSet uids;

		public GetMailCaller(KrollDict credentials, KrollFunction cb, String f, IndexSet u) {
			super(credentials, cb, false);
			folder = f;
			uids = u;
		}

		protected IMAPOperation createOperation(IMAPSession session) {
			int requestKind = IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaders |
			IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaderSubject |
			IMAPMessagesRequestKind.IMAPMessagesRequestKindExtraHeaders |
			IMAPMessagesRequestKind.IMAPMessagesRequestKindStructure;

			IMAPFetchMessagesOperation op = session.fetchMessagesByUIDOperation(folder, requestKind, uids);

			// TODO - should be passed in
			ArrayList extra_headers = new ArrayList();
			extra_headers.add("Received-SPF");
			op.setExtraHeaders(extra_headers);
			return op;
		}

		protected Object formatResult(IMAPOperation operation) {
			java.util.List<IMAPMessage> messages = ((IMAPFetchMessagesOperation)operation).messages();
			ArrayList result = new ArrayList();
			for(IMAPMessage message : messages) {
				KrollDict email_result = new KrollDict();
				email_result.put("uid", message.uid());
				email_result.put("sender_name", (message.header().sender().displayName() != null) ? message.header().sender().displayName() : "");
				email_result.put("sender_mailbox", (message.header().sender().mailbox() != null) ? message.header().sender().mailbox() : "");
				email_result.put("subject", (message.header().subject() != null) ? message.header().subject() : "");
				email_result.put("received_time", (message.header().receivedDate() != null) ? message.header().receivedDate().toString() : "");
				email_result.put("has_attachments", (message.attachments() != null) && message.attachments().size() > 0 ? true : false);

				for(String hname : message.header().allExtraHeadersNames()) {
					String extra_header = message.header().extraHeaderValueForName(hname);
					if(extra_header != null) {
						email_result.put(hname, extra_header);
					}
				}

				result.add(email_result);
			}

			return result.toArray(new KrollDict[result.size()]);
		}
	}

	// Get detailed info about one specific email by its uid
	@Kroll.method
	public void getMailInfo(KrollDict imap, String folder, long uid, KrollFunction cb) {
		CallbackCaller caller = new GetMailInfoCaller(imap, cb, uid, folder);
		caller.start();
	}
	private class GetMailInfoCaller extends CallbackCallerIMAP {
		private String folder;
		private long uid;

		public GetMailInfoCaller(KrollDict credentials, KrollFunction cb, long u, String f) {
			super(credentials, cb, false);
			uid = u;
			folder = f;
		}

		protected IMAPOperation createOperation(IMAPSession session) {
			return session.fetchMessageByUIDOperation(folder, uid);
		}

		protected Object formatResult(IMAPOperation operation) {
			byte[] data = ((IMAPFetchContentOperation)operation).data();
			MessageParser parser = MessageParser.messageParserWithData(data);

			KrollDict email = _messageToJSON(parser);
			if(parser != null) {
				email.put("body", parser.htmlBodyRendering());
				if(parser.attachments() != null) {
					ArrayList attachments = new ArrayList();
					for(AbstractPart attachment_abstract : parser.attachments()) {
						Attachment attachment = (Attachment)attachment_abstract;
						KrollDict attachment_object = new KrollDict();

						attachment_object.put("file_name", attachment.filename() == null ? "" : attachment.filename());
						attachment_object.put("mime_type", attachment.mimeType() == null ? "" : attachment.mimeType());
						attachment_object.put("data", attachment.data() == null ? "" : Base64.encodeToString(attachment.data(), Base64.NO_WRAP));

						attachments.add(attachment_object);
					}
					email.put("attachments", attachments.toArray(new Object[attachments.size()]));
				}
			}
			return email;
		}
	}

	// Get a basic skeleton of an email
	@Kroll.method
	public KrollDict compose() {
		KrollDict email_data = _getEmailStructure();
		KrollDict email_header = _getHeaderStructure();
		KrollDict email_address = _getAddressStructure();

		_applyHeader(email_header, email_data);
		_applyAddress(email_address, email_data);

		return email_data;
	}


	// Send an email
	@Kroll.method
	public void send(KrollDict smtp, KrollDict email, KrollFunction cb) {
			KrollDict email_addresses = email.getKrollDict("addresses");
			String subject = (String)(email.get("subject"));
			KrollDict from = email_addresses.getKrollDict("from");

			int receiver_count = 0;
			if(email_addresses.get("to") != null) {
				receiver_count += ((Object[])(email_addresses.get("to"))).length;
			}
			if(email_addresses.get("cc") != null) {
				receiver_count += ((Object[])(email_addresses.get("cc"))).length;
			}
			if(email_addresses.get("bcc") != null) {
				receiver_count += ((Object[])(email_addresses.get("bcc"))).length;
			}

	    if((subject != null) && (from != null) && (receiver_count > 0)) {
				CallbackCaller caller = new SendEmailCaller(smtp, cb, email);
				caller.start();
			} else {
				Log.i("Sending email", "Cannot send email without a subject, a 'from' section, and some destination");
			}

	}
	private class SendEmailCaller extends CallbackCallerSMTP {
		private KrollDict email;
		private KrollDict sent_email;

		public SendEmailCaller(KrollDict credentials, KrollFunction cb, KrollDict e) {
			super(credentials, cb, true);
			email = e;
			sent_email = new KrollDict();
		}

		protected SMTPOperation createOperation(SMTPSession session) {
			KrollDict email_headers = email.getKrollDict("headers");
			KrollDict email_addresses = email.getKrollDict("addresses");

			MessageBuilder builder = new MessageBuilder();

			String subject = (String)(email.get("subject"));
			KrollDict from = email_addresses.getKrollDict("from");

			// subject
			builder.header().setSubject(subject);

			// From
			builder.header().setFrom(Address.addressWithDisplayName((String)from.get("name"), (String)from.get("mailbox")));

			// To
			if(email_addresses.get("to") != null) {
				ArrayList to = new ArrayList();
				Object[] targets = ((Object[])(email_addresses.get("to")));
				if(targets != null) {
					for(Object t : targets) {
						HashMap target = (HashMap)t;
						to.add(Address.addressWithDisplayName((String)target.get("name"), (String)target.get("mailbox")));
					}
					builder.header().setTo(to);
				}
			}
			if(email_addresses.get("cc") != null) {
				ArrayList cc = new ArrayList();
				Object[] targets = ((Object[])(email_addresses.get("cc")));
				if(targets != null) {
					for(Object t : targets) {
						HashMap target = (HashMap)t;
						cc.add(Address.addressWithDisplayName((String)target.get("name"), (String)target.get("mailbox")));
					}
					builder.header().setCc(cc);
				}
			}
			if(email_addresses.get("bcc") != null) {
				ArrayList bcc = new ArrayList();
				Object[] targets = ((Object[])(email_addresses.get("bcc")));
				if(targets != null) {
					for(Object t : targets) {
						HashMap target = (HashMap)t;
						bcc.add(Address.addressWithDisplayName((String)target.get("name"), (String)target.get("mailbox")));
					}
					builder.header().setBcc(bcc);
				}
			}

			// Copy headers
			for(String key : email_headers.keySet()) {
				builder.header().setExtraHeader(key, (String)email_headers.get(key));
			}

			// Email body
			String body = (String)email.get("body");
			if(body != null) {
				builder.setHTMLBody(body);
			}

			sent_email = _messageToJSON(builder);
			return session.sendMessageOperation(builder.data());
		}

		protected Object formatResult(SMTPOperation operation) {
			return sent_email;
		}
	}
}
