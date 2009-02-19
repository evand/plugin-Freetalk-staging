package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.XMLTree;
import plugins.Freetalk.XMLTree.XMLElement;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

public final class WoTMessageListXML {
	private static final int XML_FORMAT_VERSION = 1;
	
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	public static void encode(WoTMessageManager messageManager, WoTOwnMessageList list, OutputStream os) throws TransformerException, ParserConfigurationException, NoSuchMessageException  {
		synchronized(list) {
			StreamResult resultStream = new StreamResult(os);

			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			DOMImplementation impl = xmlBuilder.getDOMImplementation();
			Document xmlDoc = impl.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageListTag = xmlDoc.createElement("MessageList");
			messageListTag.setAttribute("Version", Integer.toString(XML_FORMAT_VERSION)); /* Version of the XML format */
			
			for(MessageList.MessageReference ref : list) {
				OwnMessage message = messageManager.getOwnMessage(ref.getMessageID());
				if(message.wasInserted() == false)
					throw new RuntimeException("Trying to convert a MessageList to XML which contains a not inserted message.");
				
				Element messageTag = xmlDoc.createElement("Message");
				messageTag.setAttribute("ID", message.getID());
				messageTag.setAttribute("URI", message.getRealURI().toString());
				synchronized(mDateFormat) { /* TODO: The date is currently not used anywhere */
					messageTag.setAttribute("Date", mDateFormat.format(message.getDate()));
				}
				
				for(Board board : message.getBoards()) {
					Element boardTag = xmlDoc.createElement("Board");
					boardTag.setAttribute("Name", board.getName());
					messageTag.appendChild(boardTag);
				}
	
				messageListTag.appendChild(messageTag);
			}
			
			rootElement.appendChild(messageListTag);

			DOMSource domSource = new DOMSource(xmlDoc);
			TransformerFactory transformFactory = TransformerFactory.newInstance();
			Transformer serializer = transformFactory.newTransformer();
			
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes"); /* FIXME: Set to no before release. */
			serializer.transform(domSource, resultStream);
		}
	}
	
	/** Valid element names for MessageList XML version 1 */
	private static final HashSet<String> messageListXMLElements1 = new HashSet<String>(Arrays.asList(
		new String[] { Freetalk.PLUGIN_TITLE, "MessageList", "Message", "Board"}));
	
	public static WoTMessageList decode(WoTMessageManager messageManager, WoTIdentity author, FreenetURI uri, InputStream inputStream) throws Exception { 
		XMLTree xmlTreeGenerator = new XMLTree(messageListXMLElements1, inputStream);		
		XMLElement rootElement = xmlTreeGenerator.getRoot();
		
		rootElement = rootElement.children.get("MessageList");
		
		if(Integer.parseInt(rootElement.attrs.get("Version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + rootElement.attrs.get("version") + " > " + XML_FORMAT_VERSION);
		
		/* The message count is multiplied by 2 because if a message is posted to multiple boards, a MessageReference has to be created for each */
		ArrayList<MessageList.MessageReference> messages = new ArrayList<MessageList.MessageReference>(rootElement.children.countAll("Message")*2 + 1);
		
		for(XMLElement messageTag : rootElement.children.iterateAll("Message")) {
			String messageID = messageTag.attrs.get("ID");
			FreenetURI messageURI = new FreenetURI(messageTag.attrs.get("URI"));
			HashSet<Board> messageBoards = new HashSet<Board>(messageTag.children.countAll("Board") + 1);
			
			for(XMLElement boardTag : messageTag.children.iterateAll("Board"))
				messageBoards.add(messageManager.getOrCreateBoard(boardTag.attrs.get("Name")));
			
			for(Board board : messageBoards)
				messages.add(new MessageList.MessageReference(messageID, messageURI, board));
		}
		
		return new WoTMessageList(author, uri, messages);
	}
}