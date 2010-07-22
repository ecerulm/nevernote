/*
 * This file is part of NeverNote 
 * Copyright 2009 Randy Baumgarte
 * 
 * This file may be licensed under the terms of of the
 * GNU General Public License Version 2 (the ``GPL'').
 *
 * Software distributed under the License is distributed
 * on an ``AS IS'' basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the GPL for the specific language
 * governing rights and limitations.
 *
 * You should have received a copy of the GPL along with this
 * program. If not, go to http://www.gnu.org/licenses/gpl.html
 * or write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
*/


package cx.fbn.nevernote.xml;

import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.xml.QDomDocument;
import com.trolltech.qt.xml.QDomElement;
import com.trolltech.qt.xml.QDomNode;
import com.trolltech.qt.xml.QDomNodeList;
import com.trolltech.qt.xml.QDomText;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.evernote.EnCrypt;

public class XMLCleanup {
	private String content;
	private QDomDocument doc;
	private final List<String> resources;
	
	public XMLCleanup() {
		resources = new ArrayList<String>();
	}
	
	
	public void setValue(String text) {
		content = text;
/*		content = content.replace("<HR>", "<hr/>");
		content = content.replace("<hr>", "<hr/>");
		content = content.replace("</HR>", "");
		content = content.replace("</hr>", ""); */
	}
	public String getValue() {
		return content;
	}
	// Validate the contents of the note.  Change unsupported things	
	public void validate() {
		doc = new QDomDocument();
		int br = content.lastIndexOf("</en-note>");
		content = new String(content.substring(0,br));
		String newContent;
		int k = content.indexOf("<en-note");

		
		newContent = new String(content.substring(k));
		
		
		// Fix the background color
		

		newContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" 
					+"<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">\n"
					+newContent 
					+"</en-note>";

		QDomDocument.Result result = doc.setContent(newContent);
		if (!result.success) {
			System.out.println("DOM error in XMLValidator.validate()");
			System.out.println(newContent);
			System.out.println("Location : Line-"+result.errorLine +" Column-" + result.errorColumn);
			System.out.println("Exiting");
			System.exit(16);
			return;
		}
		
		QDomNodeList noteAnchors = doc.elementsByTagName("en-note");
		int noteCount = noteAnchors.length();
		for (int i=noteCount-1; i>=0; i--) {
			if (noteAnchors.at(i).toElement().hasAttribute("style")) {
				String style = noteAnchors.at(i).toElement().attribute("style");
				int startColor = style.indexOf("background-color:");
				if (startColor > -1) {
					String color = style.substring(startColor+17);
					color = color.substring(0,color.indexOf(";"));
					noteAnchors.at(i).toElement().setAttribute("bgcolor", color);
				}
			}
		}
		
		scanTags();
		
		// Remove invalid elements & attributes
		// Modify en-media tags
		QDomNodeList anchors;
		for (String key : Global.invalidAttributes.keySet()) {
			anchors = doc.elementsByTagName(key);
			int enMediaCount = anchors.length();
			for (int i=enMediaCount-1; i>=0; i--) {
				QDomElement element = anchors.at(i).toElement();
				ArrayList<String> names = Global.invalidAttributes.get(element.nodeName().toLowerCase());
				if (names != null) {	
					for (int j=0; j<names.size(); j++) {
						element.removeAttribute(names.get(j));
					}
				}
			}
		}

		List<String> elements = Global.invalidElements;
		for (int j=0; j<elements.size(); j++) {
			anchors = doc.elementsByTagName(elements.get(j));
			int enMediaCount = anchors.length();
			for (int i=enMediaCount-1; i>=0; i--) {
				QDomElement element = anchors.at(i).toElement();
				element.setTagName("span");
			}
		}
		content = doc.toString();

	}
	// Start looking through the tree.
	private void scanTags() {
//		System.out.println("scanTags start");
//		QDomElement element = doc.firstChildElement();
//		parseChildren(element.firstChild());	
		
		if (doc.hasChildNodes())
			parseNodes(doc.childNodes());
		return;
	}
	
	private void parseNodes(QDomNodeList nodes) {
		for (int i=0; i<nodes.size(); i++) {
			QDomNode node = nodes.at(i);
			if (node.hasChildNodes())
				parseNodes(node.childNodes());
			fixNode(node);
		}
	}
	
/*	
	// Parse through individual nodes
	private void parseChildren(QDomNode node) {
		System.out.println("Starting parseChildren " +node.toElement().nodeName() +" : " +node.toElement().text());
		for(; !node.isNull(); node = node.nextSibling()) {
			if (node.hasChildNodes()) {
				QDomNodeList l = node.childNodes();
				
				for (int i=0; i<l.size(); i++)  {
					System.out.println("Child node size: " +l.size() +" " +i);
					parseChildren(l.at(i));
				}
			}
			fixNode(node);
		}
	}
	
*/	
	
	// Fix the contents of the node back to ENML.
	private void fixNode(QDomNode node) {
		QDomElement scanChecked = node.toElement();
		if (scanChecked.hasAttribute("checked")) {
			System.out.println(scanChecked.attribute("checked"));
			if (!scanChecked.attribute("checked").equalsIgnoreCase("true"))
				scanChecked.setAttribute("checked", "false");
		}
		if (node.nodeName().equalsIgnoreCase("#comment") || node.nodeName().equalsIgnoreCase("script")) {
			node.parentNode().removeChild(node);
		}
		if (node.nodeName().equalsIgnoreCase("input")) {
			QDomElement e = node.toElement();
			e.setTagName("en-todo");
			String value = e.attribute("value");
			e.removeAttribute("value");
			e.removeAttribute("unchecked");
			e.setAttribute("checked", value);
			e.removeAttribute("onclick");
			e.removeAttribute("type");
		}


		if (node.nodeName().equalsIgnoreCase("a")) {
			QDomElement e = node.toElement();
			String enTag = e.attribute("en-tag");
			if (enTag.equalsIgnoreCase("en-media")) {
				e.setTagName("en-media");
				e.removeAttribute("en-type");
				e.removeAttribute("en-tag");
				e.removeAttribute("en-new");
				resources.add(e.attribute("guid"));
				e.removeAttribute("href");
				e.removeAttribute("guid");
				e.setNodeValue("");
				e.removeChild(e.firstChildElement());
			}
		}
		// Restore image resources
		if (node.nodeName().equalsIgnoreCase("img")) {
			QDomElement e = node.toElement();
			String enType = e.attribute("en-tag");
			
			// Check if we have an en-crypt tag.  Change it from an img to en-crypt
			if (enType.equalsIgnoreCase("en-crypt")) {
				
				String encrypted = e.attribute("alt");
				
				QDomText crypt = doc.createTextNode(encrypted);
				e.appendChild(crypt);
				
				e.removeAttribute("v:shapes");
				e.removeAttribute("en-tag");
				e.removeAttribute("contenteditable");
				e.removeAttribute("alt");
				e.removeAttribute("src");
				e.removeAttribute("id");
				e.removeAttribute("onclick");
				e.removeAttribute("onmouseover");
				e.setTagName("en-crypt");
				node.removeChild(e);
				return;
			}
			
			// If we've gotten this far, we have an en-media tag
			e.setTagName(enType);
			resources.add(e.attribute("guid"));
			e.removeAttribute("guid");
			e.removeAttribute("src");
			e.removeAttribute("en-new");
			e.removeAttribute("en-tag");
		}
		
		// Tags like <ul><ul><li>1</li></ul></ul> are technically valid, but Evernote 
		// expects that a <ul> tag only has a <li>, so we will need to change them
		// to this:  <ul><li><ul><li>1</li></ul></li></ul>
		if (node.nodeName().equalsIgnoreCase("ul")) {
			QDomNode firstChild = node.firstChild();
			QDomElement childElement = firstChild.toElement();
			if (childElement.nodeName().equalsIgnoreCase("ul")) {
				QDomElement newElement = doc.createElement("li");
				node.insertBefore(newElement, firstChild);
				node.removeChild(firstChild);
				newElement.appendChild(firstChild);
			}
		}
		
		if (node.nodeName().equalsIgnoreCase("en-crypt-temp")) {
			QDomElement e = node.toElement();
			String slot = e.attribute("slot");
			e.removeAttribute("slot");
			String password = Global.passwordSafe.get(slot);
			Global.passwordSafe.remove(slot);
			EnCrypt crypt = new EnCrypt();
			String encrypted = crypt.encrypt(e.text(), password, 64); 
			
			QDomText newText = doc.createTextNode(encrypted);
			e.appendChild(newText);
			e.removeChild(e.firstChild());
			e.setTagName("en-crypt");
		}
		if (node.nodeName().equalsIgnoreCase("en-hilight")) {
			QDomElement e = node.toElement();
			QDomText newText = doc.createTextNode(e.text());
			e.parentNode().replaceChild(newText,e);
		}
		if (node.nodeName().equalsIgnoreCase("span")) {
			QDomElement e = node.toElement();
			if (e.attribute("class").equalsIgnoreCase("en-hilight") || e.attribute("class").equalsIgnoreCase("en-spell")) {
				QDomText newText = doc.createTextNode(e.text());
				e.parentNode().replaceChild(newText,e);
			}
			if (e.attribute("pdfnavigationtable").equalsIgnoreCase("true")) {
				node.parentNode().removeChild(node);
			}
		}
	}


	
	// Return old resources we've found
	public List<String> getResources() {
		return resources;
	}

}

	