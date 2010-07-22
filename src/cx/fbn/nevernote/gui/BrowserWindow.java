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

package cx.fbn.nevernote.gui;

import java.io.File;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import com.evernote.edam.limits.Constants;
import com.evernote.edam.type.Data;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;
import com.evernote.edam.type.Tag;
import com.trolltech.qt.core.QByteArray;
import com.trolltech.qt.core.QDataStream;
import com.trolltech.qt.core.QDateTime;
import com.trolltech.qt.core.QEvent;
import com.trolltech.qt.core.QFile;
import com.trolltech.qt.core.QFileSystemWatcher;
import com.trolltech.qt.core.QIODevice;
import com.trolltech.qt.core.QMimeData;
import com.trolltech.qt.core.QUrl;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QCalendarWidget;
import com.trolltech.qt.gui.QClipboard;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QComboBox;
import com.trolltech.qt.gui.QDateEdit;
import com.trolltech.qt.gui.QDesktopServices;
import com.trolltech.qt.gui.QFileDialog;
import com.trolltech.qt.gui.QFontDatabase;
import com.trolltech.qt.gui.QFormLayout;
import com.trolltech.qt.gui.QGridLayout;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QIcon;
import com.trolltech.qt.gui.QImage;
import com.trolltech.qt.gui.QKeySequence;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QListWidgetItem;
import com.trolltech.qt.gui.QMatrix;
import com.trolltech.qt.gui.QMessageBox;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QShortcut;
import com.trolltech.qt.gui.QTimeEdit;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QFileDialog.AcceptMode;
import com.trolltech.qt.gui.QFileDialog.FileMode;
import com.trolltech.qt.network.QNetworkRequest;
import com.trolltech.qt.webkit.QWebPage;
import com.trolltech.qt.webkit.QWebSettings;
import com.trolltech.qt.webkit.QWebView;
import com.trolltech.qt.webkit.QWebPage.WebAction;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.dialog.EnCryptDialog;
import cx.fbn.nevernote.dialog.EnDecryptDialog;
import cx.fbn.nevernote.dialog.InsertLinkDialog;
import cx.fbn.nevernote.dialog.TableDialog;
import cx.fbn.nevernote.dialog.TagAssign;
import cx.fbn.nevernote.evernote.EnCrypt;
import cx.fbn.nevernote.signals.NoteResourceSignal;
import cx.fbn.nevernote.signals.NoteSignal;
import cx.fbn.nevernote.sql.DatabaseConnection;
import cx.fbn.nevernote.utilities.ApplicationLogger;

public class BrowserWindow extends QWidget {

	public final QLineEdit titleLabel;
	private final QLineEdit urlText;
	private final QLabel authorLabel;
	private final QLineEdit authorText;
	public final TagLineEdit tagEdit;
	public final QLabel tagLabel;
	private final QLabel urlLabel;
	private final QLabel alteredLabel;
	private final QDateEdit alteredDate;
	private final QTimeEdit alteredTime;
	private final QDateEdit createdDate;
	private final QTimeEdit createdTime;
	private final QLabel subjectLabel;
	private final QDateEdit subjectDate;
	private final QTimeEdit subjectTime;
	public final QComboBox notebookBox;
	private final QLabel notebookLabel;
	private final QLabel createdLabel;
	public final QComboBox fontSize;
	private boolean extendedOn;
	public boolean buttonsVisible;
	private final String iconPath = new String("classpath:cx/fbn/nevernote/icons/");
	private final ContentView browser;
	private List<Tag> allTags;
	private List<String> currentTags;
	public NoteSignal noteSignal;
	private List<Notebook> notebookList;
	private Note currentNote;
	private String saveNoteTitle;
	private String saveTagList;
	private boolean insideList;
//	private String selectedText;
	private final DatabaseConnection conn;
	private final QCalendarWidget createdCalendarWidget;
	private final QCalendarWidget alteredCalendarWidget;
	private final QCalendarWidget subjectCalendarWidget;

	public final QPushButton undoButton;
	public final QPushButton redoButton;
	public final QPushButton cutButton;
	public final QPushButton copyButton;
	public final QPushButton pasteButton;
	public final QPushButton boldButton;
	public final QPushButton underlineButton;
	public final QPushButton italicButton;
	public final Signal0 focusLost;
	public final NoteResourceSignal resourceSignal;

	public QPushButton rightAlignButton;
	public QPushButton leftAlignButton;
	public QPushButton centerAlignButton;

	public final QPushButton strikethroughButton;
	public final QPushButton hlineButton;
	public final QPushButton indentButton;
	public final QPushButton outdentButton;
	public final QPushButton bulletListButton;
	public final QPushButton numberListButton;

	public final QShortcut focusTitleShortcut;
	public final QShortcut focusTagShortcut;
	public final QShortcut focusNoteShortcut;
	public final QShortcut focusUrlShortcut;
	public final QShortcut focusAuthorShortcut;
	
	public final QComboBox fontList;
	public final QPushButton fontColor;
	private final ColorMenu fontColorMenu;
	public final QPushButton fontHilight;
//	public final ColorComboBox fontHilight;
	private final ColorMenu fontHilightColorMenu;
	public final QFileSystemWatcher fileWatcher;
	public int cursorPosition;
	private boolean forceTextPaste = false;
	private String selectedFile;
	private String currentHyperlink;
	private final ApplicationLogger logger;
	
	
	public BrowserWindow(DatabaseConnection c) {
		logger = new ApplicationLogger("browser.log");
		logger.log(logger.HIGH, "Setting up browser");
		
		fileWatcher = new QFileSystemWatcher();
//		fileWatcher.fileChanged.connect(this, "fileChanged(String)");
		noteSignal = new NoteSignal();
		titleLabel = new QLineEdit();
		titleLabel.setMaxLength(Constants.EDAM_NOTE_TITLE_LEN_MAX);
		urlText = new QLineEdit();
		authorText = new QLineEdit();
		urlLabel = new QLabel();
		authorLabel = new QLabel();
		conn = c;
		
		focusLost = new Signal0();

		tagEdit = new TagLineEdit(allTags);
		tagLabel = new QLabel("Tags:");
		tagEdit.focusLost.connect(this, "modifyTagsTyping()");

		createdCalendarWidget = new QCalendarWidget();
		createdDate = new QDateEdit();
		createdDate.setDisplayFormat(Global.getDateFormat());
		createdDate.setCalendarPopup(true);
		createdDate.setCalendarWidget(createdCalendarWidget);
		createdTime = new QTimeEdit();
		createdDate.dateChanged.connect(this, "createdChanged()");
		createdTime.timeChanged.connect(this, "createdChanged()");

		alteredCalendarWidget = new QCalendarWidget();
		alteredDate = new QDateEdit();
		alteredDate.setDisplayFormat(Global.getDateFormat());
		alteredDate.setCalendarPopup(true);
		alteredDate.setCalendarWidget(alteredCalendarWidget);
		alteredTime = new QTimeEdit();
		alteredLabel = new QLabel("Altered:");
		alteredDate.dateChanged.connect(this, "alteredChanged()");
		alteredTime.timeChanged.connect(this, "alteredChanged()");

		subjectCalendarWidget = new QCalendarWidget();
		subjectDate = new QDateEdit();
		subjectDate.setDisplayFormat(Global.getDateFormat());
		subjectDate.setCalendarPopup(true);
		subjectDate.setCalendarWidget(subjectCalendarWidget);
		subjectTime = new QTimeEdit();
		subjectLabel = new QLabel("Subject Date:");
		subjectDate.dateChanged.connect(this, "subjectDateTimeChanged()");
		subjectTime.timeChanged.connect(this, "subjectDateTimeChanged()");
		authorText.textChanged.connect(this, "authorChanged()");
		urlText.textChanged.connect(this, "sourceUrlChanged()");

		notebookBox = new QComboBox();
		notebookLabel = new QLabel("Notebook");
		createdLabel = new QLabel("Created:");
		// selectedText = new String();

		urlLabel.setVisible(false);
		urlText.setVisible(false);
		authorLabel.setVisible(false);
		authorText.setVisible(false);
		createdDate.setVisible(false);
		alteredLabel.setVisible(false);
		//notebookBox.setVisible(false);
		notebookLabel.setVisible(false);
		createdLabel.setVisible(false);
		createdTime.setVisible(false);
		alteredDate.setVisible(false);
		alteredTime.setVisible(false);
		subjectLabel.setVisible(false);
		subjectDate.setVisible(false);
		subjectTime.setVisible(false);
		extendedOn = false;
		buttonsVisible = true;
		setAcceptDrops(true);

		browser = new ContentView(this);
		browser.page().setLinkDelegationPolicy(
				QWebPage.LinkDelegationPolicy.DelegateAllLinks);
		browser.linkClicked.connect(this, "linkClicked(QUrl)");
		currentHyperlink = "";
		
		QVBoxLayout v = new QVBoxLayout();
		QFormLayout notebookLayout = new QFormLayout();
		QGridLayout dateLayout = new QGridLayout();
		titleLabel.setReadOnly(false);
		titleLabel.editingFinished.connect(this, "titleEdited()");
		browser.page().contentsChanged.connect(this, "contentChanged()");
		browser.page().selectionChanged.connect(this, "selectionChanged()");
		browser.page().mainFrame().javaScriptWindowObjectCleared.connect(this,
				"exposeToJavascript()");

		notebookBox.activated.connect(this, "notebookChanged()");
		resourceSignal = new NoteResourceSignal();
		
		QHBoxLayout tagLayout = new QHBoxLayout();
		v.addWidget(titleLabel, 0);
		notebookLayout.addRow(notebookLabel, notebookBox);
		tagLayout.addLayout(notebookLayout, 0);
		tagLayout.stretch(4);
		tagLayout.addWidget(tagLabel, 0);
		tagLayout.addWidget(tagEdit, 1);
		v.addLayout(tagLayout);

		QHBoxLayout urlLayout = new QHBoxLayout();
		urlLayout.addWidget(urlLabel, 0);
		urlLayout.addWidget(urlText, 0);
		v.addLayout(urlLayout);

		QHBoxLayout authorLayout = new QHBoxLayout();
		authorLayout.addWidget(authorLabel, 0);
		authorLayout.addWidget(authorText, 0);
		v.addLayout(authorLayout);

		dateLayout.addWidget(createdLabel, 0, 0);
		dateLayout.addWidget(createdDate, 0, 1);
		dateLayout.addWidget(createdTime, 0, 2);
		dateLayout.setColumnStretch(9, 100);
		dateLayout.addWidget(alteredLabel, 0, 3);
		dateLayout.addWidget(alteredDate, 0, 4);
		dateLayout.addWidget(alteredTime, 0, 5);
		dateLayout.addWidget(subjectLabel, 0, 6);
		dateLayout.addWidget(subjectDate, 0, 7);
		dateLayout.addWidget(subjectTime, 0, 8);
		v.addLayout(dateLayout, 0);

		undoButton = newEditorButton("undo", "Undo Change");
		redoButton = newEditorButton("redo", "Redo Change");
		cutButton = newEditorButton("cut", "Cut");
		copyButton = newEditorButton("copy", "Copy");
		pasteButton = newEditorButton("paste", "Paste");
		boldButton = newEditorButton("bold", "Bold");
		underlineButton = newEditorButton("underline", "Underline");
		italicButton = newEditorButton("italic", "Italic");

		rightAlignButton = newEditorButton("justifyRight", "Right Align");
		leftAlignButton = newEditorButton("justifyLeft", "Left Align");
		centerAlignButton = newEditorButton("justifyCenter", "Center Align");

		strikethroughButton = newEditorButton("strikethrough", "Strikethrough");
		hlineButton = newEditorButton("hline", "Insert Horizontal Line");
		indentButton = newEditorButton("indent", "Shift Right");
		outdentButton = newEditorButton("outdent", "Shift Left");
		bulletListButton = newEditorButton("bulletList", "Bullet List");
		numberListButton = newEditorButton("numberList", "Number List");


		QHBoxLayout buttonLayout;
		buttonLayout = new QHBoxLayout();
		buttonLayout.setSpacing(0);
		v.addLayout(buttonLayout);
		
		buttonLayout.addWidget(undoButton);
		buttonLayout.addWidget(redoButton);

		buttonLayout.addWidget(newSeparator(), 0);
		buttonLayout.addWidget(cutButton);
		buttonLayout.addWidget(copyButton);
		buttonLayout.addWidget(pasteButton);

		buttonLayout.addWidget(newSeparator(), 0);
		buttonLayout.addWidget(boldButton);
		buttonLayout.addWidget(italicButton);
		buttonLayout.addWidget(underlineButton);
		buttonLayout.addWidget(strikethroughButton);
		
		buttonLayout.addWidget(newSeparator(), 0);
		buttonLayout.addWidget(leftAlignButton);
		buttonLayout.addWidget(centerAlignButton);
		buttonLayout.addWidget(rightAlignButton);

		buttonLayout.addWidget(newSeparator(), 0);
		buttonLayout.addWidget(hlineButton);

		buttonLayout.addWidget(indentButton);
		buttonLayout.addWidget(outdentButton);
		buttonLayout.addWidget(bulletListButton);
		buttonLayout.addWidget(numberListButton);

		// Setup the font & font size combo boxes
		buttonLayout.addWidget(newSeparator(), 0);
		fontList = new QComboBox();
		fontSize = new QComboBox();
		fontList.setToolTip("Font");
		fontSize.setToolTip("Font Size");
		fontList.activated.connect(this, "fontChanged(String)");
		fontSize.activated.connect(this, "fontSizeChanged(String)");
		buttonLayout.addWidget(fontList, 0);
		buttonLayout.addWidget(fontSize, 0);
		QFontDatabase fonts = new QFontDatabase();
		List<String> fontFamilies = fonts.families();
		for (int i = 0; i < fontFamilies.size(); i++) {
			fontList.addItem(fontFamilies.get(i));
			if (i == 0) {
				loadFontSize(fontFamilies.get(i));
			}
		}

		buttonLayout.addWidget(newSeparator(), 0);
		fontColor = newEditorButton("fontColor", "Font Color");
		fontColorMenu = new ColorMenu(this);
		fontColor.setMenu(fontColorMenu.getMenu());
		fontColorMenu.getMenu().triggered.connect(this, "fontColorClicked()");
		buttonLayout.addWidget(fontColor);
		fontHilight = newEditorButton("fontHilight", "Font Hilight Color");
		fontHilightColorMenu = new ColorMenu(this);
		fontHilight.setMenu(fontHilightColorMenu.getMenu());
		fontHilightColorMenu.getMenu().triggered.connect(this, "fontHilightClicked()");
		buttonLayout.addWidget(fontHilight);

		buttonLayout.addWidget(new QLabel(), 1);
		v.addWidget(browser, 1);
//		v.addLayout(buttonLayout,0);
		setLayout(v);

		browser.downloadAttachmentRequested.connect(this,
				"downloadAttachment(QNetworkRequest)");
		browser.downloadImageRequested.connect(this,
				"downloadImage(QNetworkRequest)");
		setTabOrder(notebookBox, tagEdit);
		setTabOrder(tagEdit, browser);
		
		focusNoteShortcut = new QShortcut(this);
		setupShortcut(focusNoteShortcut, "Focus_Note");
		focusNoteShortcut.activated.connect(this, "focusNote()");
		focusTitleShortcut = new QShortcut(this);
		setupShortcut(focusTitleShortcut, "Focus_Title");
		focusTitleShortcut.activated.connect(this, "focusTitle()");
		focusTagShortcut = new QShortcut(this);
		setupShortcut(focusTagShortcut, "Focus_Tag");
		focusTagShortcut.activated.connect(this, "focusTag()");
		focusAuthorShortcut = new QShortcut(this);
		setupShortcut(focusAuthorShortcut, "Focus_Author");
		focusAuthorShortcut.activated.connect(this, "focusAuthor()");
		focusUrlShortcut = new QShortcut(this);
		setupShortcut(focusUrlShortcut, "Focus_Url");
		focusUrlShortcut.activated.connect(this, "focusUrl()");
		
		browser.page().mainFrame().setTextSizeMultiplier(Global.getTextSizeMultiplier());
		browser.page().mainFrame().setZoomFactor(Global.getZoomFactor());
		
		browser.page().microFocusChanged.connect(this, "microFocusChanged()");
		logger.log(logger.HIGH, "Browser setup complete");
	}

	
	private void setupShortcut(QShortcut action, String text) {
		if (!Global.shortcutKeys.containsAction(text))
			return;
		action.setKey(new QKeySequence(Global.shortcutKeys.getShortcut(text)));
	}
	
	

	
	// Getter for the QWebView
	public QWebView getBrowser() {
		return browser;
	}

	// Block signals while loading data or things are flagged as dirty by
	// mistake
	public void loadingData(boolean val) {
		logger.log(logger.EXTREME, "Entering BrowserWindow.loadingData() " +val);
		notebookBox.blockSignals(val);
		browser.page().blockSignals(val);
		browser.page().mainFrame().blockSignals(val);
		titleLabel.blockSignals(val);
		alteredDate.blockSignals(val);
		alteredTime.blockSignals(val);
		createdTime.blockSignals(val);
		createdDate.blockSignals(val);
		subjectDate.blockSignals(val);
		subjectTime.blockSignals(val);
		urlText.blockSignals(val);
		authorText.blockSignals(val);
		if (!val)
			exposeToJavascript();
		logger.log(logger.EXTREME, "Exiting BrowserWindow.loadingData() " +val);
	}

	// Enable/disable
	public void setReadOnly(boolean v) {
		setEnabled(true);
		titleLabel.setEnabled(!v);
		notebookBox.setEnabled(!v);
		tagEdit.setEnabled(!v);
		authorLabel.setEnabled(!v);
		urlText.setEnabled(!v);
		createdDate.setEnabled(!v);
		subjectDate.setEnabled(!v);
		alteredDate.setEnabled(!v);
		getBrowser().setEnabled(true);
	}
	
	// expose this class to Javascript on the web page
	private void exposeToJavascript() {
		browser.page().mainFrame().addToJavaScriptWindowObject("jambi", this);
	}

	// Custom event queue
	@Override
	public boolean event(QEvent e) {
		if (e.type().equals(QEvent.Type.FocusOut)) {
			logger.log(logger.EXTREME, "Focus lost");
			focusLost.emit();
		}
		return super.event(e);
	}

	// clear out browser
	public void clear() {
		logger.log(logger.EXTREME, "Entering BrowserWindow.clear()");
		setNote(null);
		browser.setContent(new QByteArray());
		tagEdit.setText("");
		urlLabel.setText("Source URL:");
		titleLabel.setText("");
		logger.log(logger.EXTREME, "Exiting BrowserWindow.clear()");
	}

	// get/set current note
	public void setNote(Note n) {
		currentNote = n;
		if (n == null)
			n = new Note();
		saveNoteTitle = n.getTitle();

	}

	public Note getNote() {
		return currentNote;
	}

	// New Editor Button
	private QPushButton newEditorButton(String name, String toolTip) {
		QPushButton button = new QPushButton();
		QIcon icon = new QIcon(iconPath + name + ".gif");
		button.setIcon(icon);
		button.setToolTip(toolTip);
		button.clicked.connect(this, name + "Clicked()");
		return button;
	}

	// New Separator
	private QLabel newSeparator() {
		return new QLabel("   ");
	}

	// Set the title in the window
	public void setTitle(String t) {
		titleLabel.setText(t);
		saveNoteTitle = t;
		checkNoteTitle();
	}

	// Return the current text title
	public String getTitle() {
		return titleLabel.text();
	}

	// Set the tag name string
	public void setTag(String t) {
		saveTagList = t;
		tagEdit.setText(t);
	}

	// Set the source URL
	public void setUrl(String t) {
		urlLabel.setText("Source URL:\t");
		urlText.setText(t);
	}

	public void setAuthor(String t) {
		authorLabel.setText("Author:\t");
		authorText.setText(t);
	}

	// Set the creation date
	public void setCreation(long date) {
		QDateTime dt = new QDateTime();
		dt.setTime_t((int) (date / 1000));
		createdDate.setDateTime(dt);
		createdTime.setDateTime(dt);
		createdDate.setDisplayFormat(Global.getDateFormat());
		createdTime.setDisplayFormat(Global.getTimeFormat());
	}

	// Set the creation date
	public void setAltered(long date) {
		QDateTime dt = new QDateTime();
		dt.setTime_t((int) (date / 1000));
		alteredDate.setDateTime(dt);
		alteredTime.setDateTime(dt);
		alteredDate.setDisplayFormat(Global.getDateFormat());
		alteredTime.setDisplayFormat(Global.getTimeFormat());
	}

	// Set the subject date
	public void setSubjectDate(long date) {
		QDateTime dt = new QDateTime();
		dt.setTime_t((int) (date / 1000));
		subjectDate.setDateTime(dt);
		subjectTime.setDateTime(dt);
		subjectDate.setDisplayFormat(Global.getDateFormat());
		subjectTime.setDisplayFormat(Global.getTimeFormat());
	}

	// Toggle the extended attribute information
	public void toggleInformation() {
		if (extendedOn) {
			extendedOn = false;
		} else {
			extendedOn = true;
		}
		urlLabel.setVisible(extendedOn);
		urlText.setVisible(extendedOn);
		authorText.setVisible(extendedOn);
		authorLabel.setVisible(extendedOn);
		createdDate.setVisible(extendedOn);
		createdTime.setVisible(extendedOn);
		createdLabel.setVisible(extendedOn);
		alteredLabel.setVisible(extendedOn);
		alteredDate.setVisible(extendedOn);
		alteredTime.setVisible(extendedOn);
		//notebookBox.setVisible(extendedOn);
		notebookLabel.setVisible(extendedOn);
		subjectLabel.setVisible(extendedOn);
		subjectDate.setVisible(extendedOn);
		subjectTime.setVisible(extendedOn);
	}

	public void hideButtons() {

		buttonsVisible = false;
		
		undoButton.setVisible(false);
		redoButton.setVisible(false);
		cutButton.setVisible(false);
		copyButton.setVisible(false);
		pasteButton.setVisible(false);
		boldButton.setVisible(false);
		underlineButton.setVisible(false);
		italicButton.setVisible(false);

		rightAlignButton.setVisible(false);
		leftAlignButton.setVisible(false);
		centerAlignButton.setVisible(false);

		strikethroughButton.setVisible(false);
		hlineButton.setVisible(false);
		indentButton.setVisible(false);
		outdentButton.setVisible(false);
		bulletListButton.setVisible(false);
		numberListButton.setVisible(false);

		fontList.setVisible(false);
		fontSize.setVisible(false);
		fontColor.setVisible(false);
		fontHilight.setVisible(false);

	}


	// Is the extended view on?
	public boolean isExtended() {
		return extendedOn;
	}

	// Listener for when a link is clicked
	@SuppressWarnings("unused")
	private void openFile() {
		logger.log(logger.EXTREME, "Starting openFile()");
		File fileHandle = new File(selectedFile);
		URI fileURL = fileHandle.toURI();
		String localURL = fileURL.toString();
		QUrl url = new QUrl(localURL);
		QFile file = new QFile(selectedFile);
		
		logger.log(logger.EXTREME, "Adding to fileWatcher:"+file.fileName());
		fileWatcher.addPath(file.fileName());
        
        if (!QDesktopServices.openUrl(url)) {
        	logger.log(logger.LOW, "Error opening file :" +url);
        }
	}
	
	
	// Listener for when a link is clicked
	@SuppressWarnings("unused")
	private void linkClicked(QUrl url) {
		logger.log(logger.EXTREME, "URL Clicked: " +url.toString());
		if (url.toString().substring(0,8).equals("nnres://")) {
			logger.log(logger.EXTREME, "URL is NN resource");
			if (url.toString().endsWith("/vnd.evernote.ink")) {
				logger.log(logger.EXTREME, "Unable to open ink note");
				QMessageBox.information(this, "Unable Open", "This is an ink note.\n"+
					"Ink notes are not supported since Evernote has not\n published any specifications on them\n" +
					"and I'm too lazy to figure them out by myself.");
				return;
			}
			String fullName = url.toString().substring(8);
			int index = fullName.indexOf(".");
			String guid = "";
			String type = "";
			if (index >-1) {
				type = fullName.substring(index+1);
				guid = fullName.substring(0,index);
			}
			index = guid.indexOf(Global.attachmentNameDelimeter);
			if (index > -1) {
				guid = guid.substring(0,index);
			}
			List<Resource> resList = currentNote.getResources();
			Resource res = null;
			for (int i=0; i<resList.size(); i++) {
				if (resList.get(i).getGuid().equals(guid)) {
					res = resList.get(i);
					i=resList.size();
				}
			}
			if (res == null) {
				String resGuid = Global.resourceMap.get(guid);
				if (resGuid != null) 
					res = conn.getNoteTable().noteResourceTable.getNoteResource(resGuid, true);
			}
			if (res != null) {
				String fileName;
				if (res.getAttributes() != null && 
						res.getAttributes().getFileName() != null && 
						!res.getAttributes().getFileName().trim().equals(""))
					fileName = res.getGuid()+Global.attachmentNameDelimeter+res.getAttributes().getFileName();
				else
					fileName = res.getGuid()+"."+type;
				QFile file = new QFile(Global.getDirectoryPath() +"res/"+fileName);
		        QFile.OpenMode mode = new QFile.OpenMode();
		        mode.set(QFile.OpenModeFlag.WriteOnly);
		        boolean openResult = file.open(mode);
				logger.log(logger.EXTREME, "File opened:" +openResult);
		        QDataStream out = new QDataStream(file);
		        Resource resBinary = conn.getNoteTable().noteResourceTable.getNoteResource(res.getGuid(), true);
				QByteArray binData = new QByteArray(resBinary.getData().getBody());
				resBinary = null;
				logger.log(logger.EXTREME, "Writing resource");
		        out.writeBytes(binData.toByteArray());
		        file.close();
			        
		        String whichOS = System.getProperty("os.name");
				if (whichOS.contains("Windows")) 
		        	url.setUrl("file:///"+file.fileName());
		        else
		        	url.setUrl("file://"+file.fileName());
		 //       fileWatcher.removePath(file.fileName());
				logger.log(logger.EXTREME, "Adding file watcher " +file.fileName());
				fileWatcher.addPath(file.fileName());
		        
		        // If we can't open it, then prompt the user to save it.
		        if (!QDesktopServices.openUrl(url)) {
					logger.log(logger.EXTREME, "We can't handle this.  Where do we put it?");
		        	QFileDialog dialog = new QFileDialog();
		        	dialog.show();
		        	if (dialog.exec()!=0) {
		        		List<String> fileNames = dialog.selectedFiles(); //gets all selected filenames
		        		if (fileNames.size() == 0) 
		        			return;
		        		String sf = fileNames.get(0);
		        		QFile saveFile = new QFile(sf);
				        mode.set(QFile.OpenModeFlag.WriteOnly);
				        saveFile.open(mode);
				        QDataStream saveOut = new QDataStream(saveFile);
				        saveOut.writeBytes(binData.toByteArray());
				        saveFile.close();
				        return;
		        	}
				}
			}
			return;
		}
		logger.log(logger.EXTREME, "Launching URL");
		QDesktopServices.openUrl(url);
	}

	// Listener for when BOLD is clicked
	@SuppressWarnings("unused")
	private void undoClicked() {
		browser.page().triggerAction(WebAction.Undo);
		browser.setFocus();
	}

	// Listener for when BOLD is clicked
	@SuppressWarnings("unused")
	private void redoClicked() {
		browser.page().triggerAction(WebAction.Redo);
		browser.setFocus();
	}

	// Listener for when BOLD is clicked
	@SuppressWarnings("unused")
	private void boldClicked() {
		browser.page().triggerAction(WebAction.ToggleBold);
		microFocusChanged();
		browser.setFocus();
	}

	// Listener for when Italics is clicked
	@SuppressWarnings("unused")
	private void italicClicked() {
		browser.page().triggerAction(WebAction.ToggleItalic);
		microFocusChanged();
		browser.setFocus();
	}

	// Listener for when UNDERLINE is clicked
	@SuppressWarnings("unused")
	private void underlineClicked() {
		browser.page().triggerAction(WebAction.ToggleUnderline);
		microFocusChanged();
		browser.setFocus();
	}

	// Listener for when Strikethrough is clicked
	@SuppressWarnings("unused")
	private void strikethroughClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('strikeThrough', false, '');");
		browser.setFocus();
	}

	// Listener for when cut is clicked
	@SuppressWarnings("unused")
	private void cutClicked() {
		browser.page().triggerAction(WebAction.Cut);
		browser.setFocus();
	}

	// Listener when COPY is clicked
	@SuppressWarnings("unused")
	private void copyClicked() {
		browser.page().triggerAction(WebAction.Copy);
		browser.setFocus();
	}

	// Listener when PASTE is clicked
	void pasteClicked() {
		logger.log(logger.EXTREME, "Paste Clicked");
		if (forceTextPaste) {
			pasteWithoutFormattingClicked();
			return;
		}
		QClipboard clipboard = QApplication.clipboard();
		QMimeData mime = clipboard.mimeData();
		
//		 String x = mime.html();

		if (mime.hasImage()) {
			logger.log(logger.EXTREME, "Image paste found");
			insertImage(mime);
			browser.setFocus();
			return;
		}

		if (mime.hasUrls()) {
			logger.log(logger.EXTREME, "URL paste found");
			handleUrls(mime);
			browser.setFocus();
			return;
		}
		
		String text = mime.html();
		if (text.contains("en-tag") && mime.hasHtml()) {
			logger.log(logger.EXTREME, "Intra-note paste found");
			text = fixInternotePaste(text);
			mime.setHtml(text);
			clipboard.setMimeData(mime);
		}

		logger.log(logger.EXTREME, "Final paste choice encountered");
		browser.page().triggerAction(WebAction.Paste);
		browser.setFocus();

	}

	// Paste text without formatting
	private void pasteWithoutFormattingClicked() {
		logger.log(logger.EXTREME, "Paste without format clipped");
		QClipboard clipboard = QApplication.clipboard();
		QMimeData mime = clipboard.mimeData();
		if (!mime.hasText())
			return;
		String text = mime.text();
		clipboard.setText(text);
		browser.page().triggerAction(WebAction.Paste);
		QApplication.clipboard().setMimeData(mime);
		browser.setFocus();

	}
	
	// insert date/time
	@SuppressWarnings("unused")
	private void insertDateTime() {
		String fmt = Global.getDateFormat() + " " + Global.getTimeFormat();
		String dateTimeFormat = new String(fmt);
		SimpleDateFormat simple = new SimpleDateFormat(dateTimeFormat);
		Calendar cal = Calendar.getInstance();
		
		browser.page().mainFrame().evaluateJavaScript(
			"document.execCommand('insertHtml', false, '"+simple.format(cal.getTime())+"');");
		
		browser.setFocus();

	}

	// Listener when Left is clicked
	@SuppressWarnings("unused")
	private void justifyLeftClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('JustifyLeft', false, '');");
		browser.setFocus();
	}

	// Listener when Center is clicked
	@SuppressWarnings("unused")
	private void justifyCenterClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('JustifyCenter', false, '');");
		browser.setFocus();
	}

	// Listener when Left is clicked
	@SuppressWarnings("unused")
	private void justifyRightClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('JustifyRight', false, '');");
		browser.setFocus();
	}

	// Listener when HLINE is clicked
	@SuppressWarnings("unused")
	private void hlineClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('insertHorizontalRule', false, '');");
		browser.setFocus();
	}

	// Listener when outdent is clicked
	private void outdentClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('outdent', false, '');");
		browser.setFocus();
	}

	// Listener when a bullet list is clicked
	@SuppressWarnings("unused")
	private void bulletListClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('InsertUnorderedList', false, '');");
		browser.setFocus();
	}

	// Listener when a bullet list is clicked
	@SuppressWarnings("unused")
	private void numberListClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('InsertOrderedList', false, '');");
		browser.setFocus();
	}

	// Listener when indent is clicked
	private void indentClicked() {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('indent', false, '');");
		browser.setFocus();
	}

	// Listener when the font name is changed
	@SuppressWarnings("unused")
	private void fontChanged(String font) {
		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('fontName',false,'" + font + "');");
		browser.setFocus();
	}

	// Listener when a font size is changed
	@SuppressWarnings("unused")
	private void fontSizeChanged(String font) {
		String text = browser.selectedText();
		if (text.trim().equalsIgnoreCase(""))
			return;

		String selectedText = browser.selectedText();
		String url = "<span style=\"font-size:" +font +"pt; \">"+selectedText +"</a>";
		String script = "document.execCommand('insertHtml', false, '"+url+"');";
		browser.page().mainFrame().evaluateJavaScript(script);
/*		browser.page().mainFrame().evaluateJavaScript(
				"document.execCommand('fontSize',false,'"
						+ font + "');");
*/
		browser.setFocus();
	}

	// Load the font combo box based upon the font selected
	private void loadFontSize(String name) {	
		QFontDatabase db = new QFontDatabase(); 
		fontSize.clear();
		List<Integer> points = db.pointSizes(name); 
		for (int i=0; i<points.size(); i++) { 
			fontSize.addItem(points.get(i).toString()); 
		}
		/*
		fontSize.addItem("x-small");
		fontSize.addItem("small");
		fontSize.addItem("medium");
		fontSize.addItem("large");
		fontSize.addItem("x-large");
		fontSize.addItem("xx-large");
		fontSize.addItem("xxx-large");
		*/
	}

	// Listener when a font size is changed
	@SuppressWarnings("unused")
	private void fontColorClicked() {
//		QColorDialog dialog = new QColorDialog();
//		QColor color = QColorDialog.getColor();
		QColor color = fontColorMenu.getColor();
		if (color.isValid())
			browser.page().mainFrame().evaluateJavaScript(
					"document.execCommand('foreColor',false,'" + color.name()
							+ "');");
		browser.setFocus();
	}

	// Listener for when a background color change is requested
	@SuppressWarnings("unused")
	private void fontHilightClicked() {
//		QColorDialog dialog = new QColorDialog();
//		QColor color = QColorDialog.getColor();
		QColor color = fontHilightColorMenu.getColor();
		if (color.isValid())
			browser.page().mainFrame().evaluateJavaScript(
					"document.execCommand('backColor',false,'" + color.name()
							+ "');");
		browser.setFocus();
	}
	
	// Listener for when a background color change is requested
	@SuppressWarnings("unused")
	private void superscriptClicked() {
		browser.page().mainFrame().evaluateJavaScript(
					"document.execCommand('superscript');");
		browser.setFocus();
	}
	
	// Listener for when a background color change is requested
	@SuppressWarnings("unused")
	private void subscriptClicked() {
		browser.page().mainFrame().evaluateJavaScript(
					"document.execCommand('subscript');");
		browser.setFocus();
	}
	// Insert a to-do checkbox
	@SuppressWarnings("unused")
	private void todoClicked() {
		FileNameMap fileNameMap = URLConnection.getFileNameMap();
		String script_start = new String(
				"document.execCommand('insertHtml', false, '");
		String script_end = new String("');");
		String todo = new String(
				"<input TYPE=\"CHECKBOX\" value=\"false\" onClick=\"value=checked; window.jambi.contentChanged(); \" />");
		browser.page().mainFrame().evaluateJavaScript(
				script_start + todo + script_end);
		browser.setFocus();
	}

	// Encrypt the selected text
	@SuppressWarnings("unused")
	private void encryptText() {
		String text = browser.selectedText();
		if (text.trim().equalsIgnoreCase(""))
			return;

		EnCryptDialog dialog = new EnCryptDialog();
		dialog.exec();
		if (!dialog.okPressed()) {
			return;
		}

		EnCrypt crypt = new EnCrypt();
		String encrypted = crypt.encrypt(text, dialog.getPassword().trim(), 64);

		if (encrypted.trim().equals("")) {
			QMessageBox.information(this, "Error", "Error Encrypting String");
			return;
		}
		StringBuffer imgPath = new StringBuffer(Global.getDirectoryPath());
		for (int i = 0; i < imgPath.length(); i++)
			if (imgPath.charAt(i) == '\\')
				imgPath.setCharAt(i, '/');
		StringBuffer buffer = new StringBuffer(encrypted.length() + 100);
		buffer.append("<img en-tag=\"en-crypt\" cipher=\"RC2\" hint=\""
				+ dialog.getHint().replace("'","\\'") + "\" length=\"64\" ");
		buffer.append("contentEditable=\"false\" alt=\"");
		buffer.append(encrypted);
		buffer.append("\" src=\"" + imgPath.toString()
				+ "images/encrypt.png\" ");
		Global.cryptCounter++;
		buffer.append(" id=\"crypt"+Global.cryptCounter.toString() +"\"");
		buffer.append(" onMouseOver=\"style.cursor=\\'hand\\'\"");
		buffer.append(" onClick=\"window.jambi.decryptText(\\'crypt"+Global.cryptCounter.toString() 
				+"\\', \\'"+encrypted+"\\', \\'"+dialog.getHint().replace("'", "\\&amp;apos;")+"\\');\"");
		buffer.append("style=\"display:block\" />");

		String script_start = new String(
				"document.execCommand('insertHtml', false, '");
		String script_end = new String("');");
		browser.page().mainFrame().evaluateJavaScript(
				script_start + buffer.toString() + script_end);
	}

	
	// Insert a hyperlink
	public void insertLink() {
		logger.log(logger.EXTREME, "Inserting link");
		String text = browser.selectedText();
		if (text.trim().equalsIgnoreCase(""))
			return;

		InsertLinkDialog dialog = new InsertLinkDialog();
		if (currentHyperlink != null && currentHyperlink != "") {
			dialog.setUrl(currentHyperlink);
		}
		dialog.exec();
		if (!dialog.okPressed()) {
			logger.log(logger.EXTREME, "Insert link canceled");
			return;
		}
		if (browser.insertLinkAction.text().equalsIgnoreCase("Insert Hyperlink")) {
			String selectedText = browser.selectedText();
			logger.log(logger.EXTREME, "Inserting link on text "+selectedText);
			logger.log(logger.EXTREME, "URL Link " +dialog.getUrl().trim());
			String url = "<a href=\"" +dialog.getUrl().trim()+"\" >"+selectedText +"</a>";
			String script = "document.execCommand('insertHtml', false, '"+url+"');";
			browser.page().mainFrame().evaluateJavaScript(script);
			return;
		} else {
			String js = new String( "function getCursorPos() {"
					+"var cursorPos;"
					+"if (window.getSelection) {"
					+"   var selObj = window.getSelection();"
					+"   var selRange = selObj.getRangeAt(0);"
					+"   var workingNode = window.getSelection().anchorNode.parentNode;"
					+"   while(workingNode != null) { " 
					+"      if (workingNode.nodeName.toLowerCase()=='a') workingNode.setAttribute('href','" +dialog.getUrl() +"');"
					+"      workingNode = workingNode.parentNode;"
					+"   }"
					+"}"
					+"} getCursorPos();");
				browser.page().mainFrame().evaluateJavaScript(js);
				contentChanged();
		}
		
	}
	
	// Insert a table
	public void insertTable() {
		TableDialog dialog = new TableDialog();
		dialog.exec();
		if (!dialog.okPressed()) {
			return;
		}
		
		int cols = dialog.getCols();
		int rows = dialog.getRows();
		int width = dialog.getWidth();
		boolean percent = dialog.isPercent();
		
		String newHTML = "<table border=\"1\" width=\"" +new Integer(width).toString();
		if (percent)
			newHTML = newHTML +"%";
		newHTML = newHTML + "\"><tbody>";

		for (int i=0; i<rows; i++) {
			newHTML = newHTML +"<tr>";
			for (int j=0; j<cols; j++) {
				newHTML = newHTML +"<td>&nbsp;</td>";
			}
			newHTML = newHTML +"</tr>";
		}
		newHTML = newHTML+"</tbody></table>";	
	
		String script = "document.execCommand('insertHtml', false, '"+newHTML+"');";
		browser.page().mainFrame().evaluateJavaScript(script);
	}
	
	
	// Text content changed
	@SuppressWarnings("unused")
	private void selectionChanged() {
		browser.encryptAction.setEnabled(true);
		browser.insertLinkAction.setEnabled(true);
		String scriptStart = "var selection_text = (window.getSelection()).toString();"
				+ "var range = (window.getSelection()).getRangeAt(0);"
				+ "var parent_html = range.commonAncestorContainer.innerHTML;"
				+ "if (parent_html == undefined) {window.jambi.saveSelectedText(selection_text); return;}"
				+ "var first_text = range.startContainer.nodeValue.substr(range.startOffset);"
				+ "var last_text = (range.endContainer.nodeValue).substring(0,range.endOffset);"
				+ "var start = parent_html.indexOf(first_text);"
				+ "var end = parent_html.indexOf(last_text,start+1)+last_text.length;"
				+ "var value = parent_html.substring(start,end);"
				+ "window.jambi.saveSelectedText(value);" ;
		browser.page().mainFrame().evaluateJavaScript(scriptStart);

	}

	public void saveSelectedText(String text) {
		boolean enabled = true;
		if (text.trim().length() == 0)
			enabled=false;
		if (text.indexOf("en-tag=\"en-crypt\"") >= 0)
			enabled=false;
		if (text.indexOf("<img en-tag=\"en-media\"") >= 0)
			enabled=false;
		if (text.indexOf("<a en-tag=\"en-media\"") >= 0)
			enabled=false;
		if (text.indexOf("<input ") >= 0)
			enabled=false;
		
		browser.encryptAction.setEnabled(enabled);
		browser.insertLinkAction.setEnabled(enabled);
//		selectedText = text;
	}

	// Decrypt clicked text
	public void decryptText(String id, String text, String hint) {
		EnCrypt crypt = new EnCrypt();
		String plainText = null;
		Calendar currentTime = new GregorianCalendar();
		Long l = new Long(currentTime.getTimeInMillis());
		String slot = new String(Long.toString(l));
		
		// First, try to decrypt with any keys we already have
		for (int i=0; i<Global.passwordRemember.size(); i++) {
			plainText = crypt.decrypt(text, Global.passwordRemember.get(i), 64);
			if (plainText != null) {
				slot = new String(Long.toString(l));
				Global.passwordSafe.put(slot, Global.passwordRemember.get(i));
				removeEncryption(id, plainText, false, slot);	
				return;
			}
		}
		
		
		EnDecryptDialog dialog = new EnDecryptDialog();
		dialog.setHint(hint);
		while (plainText == null || !dialog.okPressed()) {
			dialog.exec();
			if (!dialog.okPressed()) {
				return;
			}
			plainText = crypt.decrypt(text, dialog.getPassword().trim(), 64);
			if (plainText == null) {
				QMessageBox.warning(this, "Incorrect Password", "The password entered is not correct");
			}
		}
		Global.passwordSafe.put(slot, dialog.getPassword());
		removeEncryption(id, plainText, dialog.permanentlyDecrypt(), slot);
		if (dialog.rememberPassword())
			Global.passwordRemember.add(dialog.getPassword());

	}

	// Get the editor tag line
	public TagLineEdit getTagLine() {
		return tagEdit;
	}

	// Modify a note's tags
	@SuppressWarnings("unused")
	private void modifyTags() {
		TagAssign tagWindow = new TagAssign(allTags, currentTags);
		tagWindow.exec();
		if (tagWindow.okClicked()) {
			currentTags.clear();
			StringBuffer tagDisplay = new StringBuffer();

			List<QListWidgetItem> newTags = tagWindow.getTagList()
					.selectedItems();
			for (int i = 0; i < newTags.size(); i++) {
				currentTags.add(newTags.get(i).text());
				tagDisplay.append(newTags.get(i).text());
				if (i < newTags.size() - 1) {
					tagDisplay.append(Global.tagDelimeter + " ");
				}
			}
			tagEdit.setText(tagDisplay.toString());
			noteSignal.tagsChanged.emit(currentNote.getGuid(), currentTags);
		}
	}

	// Tag line has been modified by typing text
	@SuppressWarnings("unused")
	private void modifyTagsTyping() {

		String newTags = tagEdit.text();
		List<String> test = tagEdit.tagCompleter.getTagList();
		if (newTags.equalsIgnoreCase(saveTagList))
			return;

		// We know something has changed...
		String oldTagArray[] = saveTagList.split(Global.tagDelimeter);
		String newTagArray[] = newTags.split(Global.tagDelimeter);

		List<String> newTagList = new ArrayList<String>();
		List<String> oldTagList = new ArrayList<String>();

		for (int i = 0; i < oldTagArray.length; i++)
			if (!oldTagArray[i].trim().equals(""))
				oldTagList.add(oldTagArray[i]);
		for (int i = 0; i < newTagArray.length; i++)
			if (!newTagArray[i].trim().equals(""))
				newTagList.add(newTagArray[i]);

		// We now have lists of the new & old. Remove duplicates. If all
		// are removed from both then nothing has really changed
		for (int i = newTagList.size() - 1; i >= 0; i--) {
			String nTag = newTagList.get(i);
			for (int j = oldTagList.size() - 1; j >= 0; j--) {
				String oTag = oldTagList.get(j);
				if (oTag.equalsIgnoreCase(nTag)) {
					oldTagList.remove(j);
					newTagList.remove(i);
					j = -1;
				}
			}
		}

		if (oldTagList.size() != 0 || newTagList.size() != 0) {
			currentTags.clear();
			for (int i = 0; i < newTagArray.length; i++)
				if (!newTagArray[i].trim().equals(""))
					currentTags.add(newTagArray[i].trim());

			noteSignal.tagsChanged.emit(currentNote.getGuid(), currentTags);
		}

	}

	// Tab button was pressed
	public void tabPressed() {
		if (!insideList) {
			String script_start = new String(
			"document.execCommand('insertHtml', false, '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;');");
			browser.page().mainFrame().evaluateJavaScript(script_start);
		} else 
			indentClicked();
	}
	
	public void backtabPressed() {
		if (insideList)
			outdentClicked();
	}
	
	public void setInsideList() {
		insideList = true;
	}
	
	// The title has been edited
	@SuppressWarnings("unused")
	private void titleEdited() {
		// If we don't have a good note, or if the current title
		// matches the old title then we don't need to do anything
		if (currentNote == null)
			return;
		if (currentNote.getTitle().trim().equals(titleLabel.text().trim()))
			return;
		
		// If we have a real change, we need to save it.
		noteSignal.titleChanged.emit(currentNote.getGuid(), titleLabel.text());
		currentNote.setTitle(titleLabel.text());
		saveNoteTitle = titleLabel.text();
		checkNoteTitle();
	}

	// Set the list of note tags
	public void setAllTags(List<Tag> l) {
		allTags = l;
		tagEdit.setTagList(l);
	}

	// Setter for the current tags
	public void setCurrentTags(List<String> s) {
		currentTags = s;
	}

	// Save the list of notebooks
	public void setNotebookList(List<Notebook> n) {
		notebookList = n;
		loadNotebookList();
	}

	// Load the notebook list and select the current notebook
	private void loadNotebookList() {
		if (notebookBox.count() != 0)
			notebookBox.clear();
		if (notebookList == null)
			return;

		for (int i = 0; i < notebookList.size(); i++) {
			notebookBox.addItem(notebookList.get(i).getName());
			if (currentNote != null) {
				if (currentNote.getNotebookGuid().equals(
						notebookList.get(i).getGuid())) {
					notebookBox.setCurrentIndex(i);
				}
			}
		}
	}

	// Get the contents of the editor
	public String getContent() {
		return browser.page().currentFrame().toHtml();
	}

	// The note contents have changed
	public void contentChanged() {
		String content = getContent();
		checkNoteTitle();
		noteSignal.noteChanged.emit(currentNote.getGuid(), content); 
		
		
//        noteSignal.noteChanged.emit(currentNote.getGuid(), unicode);
	}

	// The notebook selection has changed
	@SuppressWarnings("unused")
	private void notebookChanged() {
		boolean changed = false;
		String n = notebookBox.currentText();
		for (int i = 0; i < notebookList.size(); i++) {
			if (n.equals(notebookList.get(i).getName())) {
				if (!notebookList.get(i).getGuid().equals(currentNote.getNotebookGuid())) {
					currentNote.setNotebookGuid(notebookList.get(i).getGuid());
					changed = true;
				}
				i = notebookList.size();
			}
		}
		
		// If the notebook changed, signal the update
		if (changed)
			noteSignal.notebookChanged.emit(currentNote.getGuid(), currentNote
					.getNotebookGuid());
	}

	// Check the note title
	private void checkNoteTitle() {
		String text = browser.page().currentFrame().toPlainText();
		if (saveNoteTitle.trim().equals("")) {
			int newLine = text.indexOf("\n");
			if (newLine > 0) {
				text = text.substring(0, newLine);
				if (text.trim().equals(""))
					text = "Untitled Note";
				titleLabel.setText(text);
			} else {
				if (text.length() > 20)
					titleLabel.setText(text.substring(0, 20));
				else {
					if (text.trim().equals(""))
						titleLabel.setText("Untitled Note");
					else
						titleLabel.setText(text);
				}
			}
			noteSignal.titleChanged.emit(currentNote.getGuid(), titleLabel
					.text());
		}
	}

	// Return the note contents so we can email them
	public String getContentsToEmail() {
		return browser.page().currentFrame().toPlainText().trim();
		/*
		 * int body = browser.page().currentFrame().toHtml().indexOf("<body>");
		 * String temp = browser.page().currentFrame().toHtml(); if (body == -1)
		 * temp = "<html><body><b>Test</b></body></html>"; else temp =
		 * "<html>"+temp.substring(body); return temp; // return
		 * urlEncode(browser.page().currentFrame().toHtml());
		 */
	}

	// Insert an image into the editor
	private void insertImage(QMimeData mime) {
		logger.log(logger.EXTREME, "Entering insertImage");
		QImage img = (QImage) mime.imageData();
		String script_start = new String(
				"document.execCommand('insertHTML', false, '");
		String script_end = new String("');");

		long now = new Date().getTime();
		String path = Global.getDirectoryPath() + "res/"
				+ (new Long(now).toString()) + ".jpg";

		// This block is just a hack to make sure we wait at least 1ms so we
		// don't
		// have collisions on image names
		long i = new Date().getTime();
		while (now == i)
			i = new Date().getTime();

		// Open the file & write the data
		QFile tfile = new QFile(path);
		tfile.open(new QIODevice.OpenMode(QIODevice.OpenModeFlag.WriteOnly));
		img.save(tfile);
		tfile.close();
		
		Resource newRes = createResource(QUrl.fromLocalFile(path).toString(), 0, "image/jpeg", false);
		if (newRes == null)
			return;
		currentNote.getResources().add(newRes);

		// do the actual insert into the note
		StringBuffer buffer = new StringBuffer(100);
		buffer.append("<img src=\"");
		buffer.append(tfile.fileName());
		buffer.append("\" en-tag=en-media type=\"image/jpeg\""
				+" hash=\""+Global.byteArrayToHexString(newRes.getData().getBodyHash()) +"\""
				+" guid=\"" +newRes.getGuid() +"\""
				+" onContextMenu=\"window.jambi.imageContextMenu('" +tfile.fileName() +"');\""
				+ " />");
		browser.page().mainFrame().evaluateJavaScript(
				script_start + buffer + script_end);

		return;
	}

	// Handle URLs that are trying to be pasted
	public void handleUrls(QMimeData mime) {
		logger.log(logger.EXTREME, "Starting handleUrls");
		FileNameMap fileNameMap = URLConnection.getFileNameMap();

		List<QUrl> urlList = mime.urls();
		String url = new String();
		String script_start = new String(
				"document.execCommand('createLink', false, '");
		String script_end = new String("');");

		for (int i = 0; i < urlList.size(); i++) {
			url = urlList.get(i).toString();
			// Find out what type of file we have
			String mimeType = fileNameMap.getContentTypeFor(url);

			// If null returned, we need to guess at the file type
			if (mimeType == null)
				mimeType = "application/"
						+ url.substring(url.lastIndexOf(".") + 1);

			// Check if we have an image or some other type of file
			if (url.substring(0, 5).equalsIgnoreCase("file:")
					&& mimeType.substring(0, 5).equalsIgnoreCase("image")) {
				handleLocalImageURLPaste(mime, mimeType);
				return;
			}
			String[] type = mimeType.split("/");
			boolean valid = validAttachment(type[1]);
			boolean smallEnough = checkFileAttachmentSize(url);
			if (smallEnough && valid
					&& url.substring(0, 5).equalsIgnoreCase("file:")
					&& !mimeType.substring(0, 5).equalsIgnoreCase("image")) {
				handleLocalAttachment(mime, mimeType);
				return;
			}
			browser.page().mainFrame().evaluateJavaScript(
					script_start + url + script_end);
		}
		return;
	}

	// If a URL being pasted is an image URL, then attach the image
	private void handleLocalImageURLPaste(QMimeData mime, String mimeType) {
		List<QUrl> urlList = mime.urls();
		String url = new String();
		String script_start_image = new String(
				"document.execCommand('insertHtml', false, '");
		String script_end = new String("');");
		StringBuffer buffer;

		// Copy the image over into the resource directory and create a new resource 
		// record for each url pasted
		for (int i = 0; i < urlList.size(); i++) {
			url = urlList.get(i).toString();

			Resource newRes = createResource(url, i, mimeType, false);
			if (newRes == null)
				return;
			currentNote.getResources().add(newRes);
			buffer = new StringBuffer(100);
			
			// Open the file & write the data
			String fileName = Global.getDirectoryPath()+"res/"+newRes.getGuid();
			QFile tfile = new QFile(fileName);
			tfile.open(new QIODevice.OpenMode(QIODevice.OpenModeFlag.WriteOnly));
			tfile.write(newRes.getData().getBody());
			tfile.close();
			fileName = fileName.replace('\\', '/');
			buffer.append(script_start_image);
			buffer.append("<img src=\"" +fileName);
//			if (mimeType.equalsIgnoreCase("image/jpg"))
//				mimeType = "image/jpeg";
			buffer.append("\" en-tag=\"en-media\" type=\"" + mimeType +"\""
					+" hash=\""+Global.byteArrayToHexString(newRes.getData().getBodyHash()) +"\""
					+" guid=\"" +newRes.getGuid() +"\""
					+" onContextMenu=\"window.jambi.imageContextMenu(&apos;" +tfile.fileName() +"&apos;);\""
					+ " />");
			buffer.append(script_end);
			browser.page().mainFrame().evaluateJavaScript(buffer.toString());
		}
		return;
	}
	

	// If a URL being pasted is a local file URL, then attach the file
	private void handleLocalAttachment(QMimeData mime, String mimeType) {
		logger.log(logger.EXTREME, "Attaching local file");
		List<QUrl> urlList = mime.urls();
		String script_start = new String(
				"document.execCommand('insertHtml', false, '");
		String script_end = new String("');");
		StringBuffer buffer;

		for (int i = 0; i < urlList.size(); i++) {
			buffer = new StringBuffer(100);
			String url = urlList.get(i).toString();

			// Start building the HTML
			String[] type = mimeType.split("/");
			String icon = findIcon(type[1]);
			if (icon.equals("attachment.png"))
				icon = findIcon(type[0]);
			if (icon.equals("attachment.png"))
				icon = findIcon(url.substring(url.lastIndexOf(".")+1));
			StringBuffer imageBuffer = new StringBuffer();
			String whichOS = System.getProperty("os.name");
			if (whichOS.contains("Windows")) 
				imageBuffer.append("file:///" + Global.getDirectoryPath()
						+ "images/" + icon);
			else
				imageBuffer.append("file://" + Global.getDirectoryPath()
						+ "images/" + icon);
			// Fix stupid Windows file separation characters
			if (whichOS.contains("Windows")) {
				for (int z = imageBuffer.indexOf("\\"); z > 0; z = imageBuffer
						.indexOf("\\")) {
					int w = imageBuffer.indexOf("\\");
					imageBuffer.replace(w, w + 1, "/");
				}

			}
			logger.log(logger.EXTREME, "Creating resource ");
			Resource newRes = createResource(url, i, mimeType, true);
			if (newRes == null)
				return;
			logger.log(logger.EXTREME, "New resource size: " +newRes.getData().getSize());
			currentNote.getResources().add(newRes);
			
			String fileName = newRes.getGuid() + Global.attachmentNameDelimeter+newRes.getAttributes().getFileName();
			// If we have a PDF, we need to setup the preview.
			if (icon.equalsIgnoreCase("pdf.png") && Global.pdfPreview()) {
				logger.log(logger.EXTREME, "Setting up PDF preview");
				if (newRes.getAttributes() != null && 
						newRes.getAttributes().getFileName() != null && 
						!newRes.getAttributes().getFileName().trim().equals(""))
					fileName = newRes.getGuid()+Global.attachmentNameDelimeter+
						newRes.getAttributes().getFileName();
				else
					fileName = newRes.getGuid()+".pdf";
				QFile file = new QFile(Global.getDirectoryPath() +"res/"+fileName);
		        QFile.OpenMode mode = new QFile.OpenMode();
		        mode.set(QFile.OpenModeFlag.WriteOnly);
		        file.open(mode);
		        QDataStream out = new QDataStream(file);
//		        Resource resBinary = conn.getNoteTable().noteResourceTable.getNoteResource(newRes.getGuid(), true);
				QByteArray binData = new QByteArray(newRes.getData().getBody());
//				resBinary = null;
		        out.writeBytes(binData.toByteArray());
		        file.close();

				PDFPreview pdfPreview = new PDFPreview();
				if (pdfPreview.setupPreview(Global.currentDir+"res/"+fileName, "pdf",0));
					imageBuffer = new StringBuffer(file.fileName()+".png");
			}
						
			logger.log(logger.EXTREME, "Generating link tags");
			buffer.append("<a en-tag=\"en-media\" guid=\"" +newRes.getGuid()+"\" ");
			buffer.append(" onContextMenu=\"window.jambi.imageContextMenu(&apos;" +Global.getDirectoryPath() +"res/"+fileName +"&apos;);\" ");
			buffer.append("type=\"" + mimeType + "\" href=\"nnres://" + fileName +"\" hash=\""+Global.byteArrayToHexString(newRes.getData().getBodyHash()) +"\" >");
			buffer.append("<img src=\"" + imageBuffer.toString()+"\" title=\"" +newRes.getAttributes().getFileName());
			buffer.append("\"></img>");
			buffer.append("</a>");
			browser.page().mainFrame().evaluateJavaScript(
					script_start + buffer.toString() + script_end);
		}
		return;
	}
	private Resource createResource(String url, int sequence, String mime, boolean attachment) {
		logger.log(logger.EXTREME, "Inside create resource");
		QFile resourceFile;
		url = new QUrl(url).toLocalFile();
		url = url.replace("/", File.separator);
    	resourceFile = new QFile(url); 
    	resourceFile.open(new QIODevice.OpenMode(QIODevice.OpenModeFlag.ReadOnly));
    	byte[] fileData = resourceFile.readAll().toByteArray();
    	resourceFile.close();
    	if (fileData.length == 0)
    		return null;
    	MessageDigest md;
    	try {
    		md = MessageDigest.getInstance("MD5");
    		md.update(fileData);
    		byte[] hash = md.digest();
  
    		Resource r = new Resource();
    		Calendar time = new GregorianCalendar();
    		long prevTime = time.getTimeInMillis();
    		while (prevTime == time.getTimeInMillis()) {
    			time = new GregorianCalendar();
    		}
    		r.setGuid(time.getTimeInMillis()+new Integer(sequence).toString());
    		r.setNoteGuid(currentNote.getGuid());
    		r.setMime(mime);
    		r.setActive(true);
    		r.setUpdateSequenceNum(0);
    		r.setWidth((short) 0);
    		r.setHeight((short) 0);
    		r.setDuration((short) 0);
    		  		
    		Data d = new Data();
    		d.setBody(fileData);
    		d.setBodyIsSet(true);
    		d.setBodyHash(hash);
    		d.setBodyHashIsSet(true);
    		r.setData(d);
    		d.setSize(fileData.length);
    		
    		int fileNamePos = url.lastIndexOf(File.separator);
    		if (fileNamePos == -1)
    			fileNamePos = url.lastIndexOf("/");
   			String fileName = url.substring(fileNamePos+1);
    		ResourceAttributes a = new ResourceAttributes();
    		a.setAltitude(0);
    		a.setAltitudeIsSet(false);
    		a.setLongitude(0);
    		a.setLongitudeIsSet(false);
    		a.setLatitude(0);
    		a.setLatitudeIsSet(false);
    		a.setCameraMake("");
    		a.setCameraMakeIsSet(false);
    		a.setCameraModel("");
    		a.setCameraModelIsSet(false);
    		a.setAttachment(attachment);
    		a.setAttachmentIsSet(true);
    		a.setClientWillIndex(false);
    		a.setClientWillIndexIsSet(true);
    		a.setRecoType("");
    		a.setRecoTypeIsSet(false);
    		a.setSourceURL(url);
    		a.setSourceURLIsSet(true);
    		a.setTimestamp(0);
    		a.setTimestampIsSet(false);
    		a.setFileName(fileName);
    		a.setFileNameIsSet(true);
    		r.setAttributes(a);
    		
    		conn.getNoteTable().noteResourceTable.saveNoteResource(r, true);
    		return r;
    	} catch (NoSuchAlgorithmException e1) {
    		e1.printStackTrace();
		}
    	return null;
	}
	

    // find the appropriate icon for an attachment
    private String findIcon(String appl) {
    	appl = appl.toLowerCase();
    	File f = new File(Global.getDirectoryPath()+"images"+File.separator +appl +".png");
    	if (f.exists())
    		return appl+".png";
    	return "attachment.png";
    }

	// Check if the account supports this type of attachment
	private boolean validAttachment(String type) {
		if (Global.isPremium())
			return true;
		if (type.equalsIgnoreCase("JPG"))
			return true;
		if (type.equalsIgnoreCase("PNG"))
			return true;
		if (type.equalsIgnoreCase("GIF"))
			return true;
		if (type.equalsIgnoreCase("MP3"))
			return true;
		if (type.equalsIgnoreCase("WAV"))
			return true;
		if (type.equalsIgnoreCase("AMR"))
			return true;
		if (type.equalsIgnoreCase("PDF"))
			return true;
		String error = "Non-premium accounts can only attach JPG, PNG, GIF, MP3, WAV, AMR, or PDF files.";
		QMessageBox.information(this, "Non-Premium Account", error);

		return false;
	}

	// Check the file attachment to be sure it isn't over 25 mb
	private boolean checkFileAttachmentSize(String url) {
		String fileName = url.substring(8);
		QFile resourceFile = new QFile(fileName);
		resourceFile.open(new QIODevice.OpenMode(
				QIODevice.OpenModeFlag.ReadOnly));
		long size = resourceFile.size();
		resourceFile.close();
		size = size / 1024 / 1024;
		if (size < 50 && Global.isPremium())
			return true;
		if (size < 25)
			return true;

		String error = "A file attachment may not exceed 25MB.";
		QMessageBox.information(this, "Attachment Size", error);
		return false;
	}


	@SuppressWarnings("unused")
	private void createdChanged() {
		QDateTime dt = new QDateTime();
		dt.setDate(createdDate.date());
		dt.setTime(createdTime.time());
		noteSignal.createdDateChanged.emit(currentNote.getGuid(), dt);

	}

	@SuppressWarnings("unused")
	private void alteredChanged() {
		QDateTime dt = new QDateTime();
		dt.setDate(alteredDate.date());
		dt.setTime(alteredTime.time());
		noteSignal.alteredDateChanged.emit(currentNote.getGuid(), dt);
	}

	@SuppressWarnings("unused")
	private void subjectDateTimeChanged() {
		QDateTime dt = new QDateTime();
		dt.setDate(subjectDate.date());
		dt.setTime(subjectTime.time());
		noteSignal.subjectDateChanged.emit(currentNote.getGuid(), dt);

	}

	@SuppressWarnings("unused")
	private void sourceUrlChanged() {
		noteSignal.sourceUrlChanged.emit(currentNote.getGuid(), urlText.text());
	}

	@SuppressWarnings("unused")
	private void authorChanged() {
		noteSignal.authorChanged.emit(currentNote.getGuid(), authorText.text());
	}

	// ************************************************************
	// * User chose to save an attachment. Pares out the request *
	// * into a guid & file. Save the result. *
	// ************************************************************
	public void downloadAttachment(QNetworkRequest request) {
		String guid;
		QFileDialog fd = new QFileDialog(this);
		fd.setFileMode(FileMode.AnyFile);
		fd.setConfirmOverwrite(true);
		fd.setWindowTitle("Save File");
		fd.setAcceptMode(AcceptMode.AcceptSave);
		fd.setDirectory(System.getProperty("user.home"));
		String name = request.url().toString();

		int pos = name.lastIndexOf(Global.attachmentNameDelimeter);
		if (pos > -1) {
			guid = name.substring(0, pos).replace("nnres://", "");
			name = name.substring(pos +Global.attachmentNameDelimeter.length());
			fd.selectFile(name);
			pos = name.lastIndexOf('.');
			if (pos > -1) {
				String mimeType = "(*." + name.substring(pos + 1)
						+ ");; All Files (*)";
				fd.setFilter(tr(mimeType));
			}
		} else {
			guid = name;
		}
		guid = guid.replace("nnres://", "").replace(Global.currentDir.replace("\\","/")+"res/", "");
		pos = guid.lastIndexOf('.');
		if (pos > 0)
			guid = guid.substring(0,pos);
		if (fd.exec() != 0 && fd.selectedFiles().size() > 0) {
			name = name.replace('\\', '/');
			Resource resBinary = conn.getNoteTable().noteResourceTable.getNoteResource(guid, true);
			QFile saveFile = new QFile(fd.selectedFiles().get(0));
			QFile.OpenMode mode = new QFile.OpenMode();
			mode.set(QFile.OpenModeFlag.WriteOnly);
			saveFile.open(mode);
			QDataStream saveOut = new QDataStream(saveFile);
			QByteArray binData = new QByteArray(resBinary.getData().getBody());
			saveOut.writeBytes(binData.toByteArray());
			saveFile.close();

		}
	}

	
	// ************************************************************
	// * User chose to save an attachment. Pares out the request *
	// * into a guid & file. Save the result. --- DONE FROM downloadAttachment now!!!!!   
	// ************************************************************
	public void downloadImage(QNetworkRequest request) {
		QFileDialog fd = new QFileDialog(this);
		fd.setFileMode(FileMode.AnyFile);
		fd.setConfirmOverwrite(true);
		fd.setWindowTitle("Save File");
		fd.setAcceptMode(AcceptMode.AcceptSave);
		fd.setDirectory(System.getProperty("user.home"));
		String name = request.url().toString();
		name = name.replace("nnres://", "");
		String dPath = Global.getDirectoryPath() + "res/";
		dPath = dPath.replace('\\', '/');
		name = name.replace(dPath, "");
		int pos = name.lastIndexOf('.');
		String guid = name;
		if (pos > -1) {
			String mimeType = "(*." + name.substring(pos + 1)
			+ ");; All Files (*)";
				fd.setFilter(tr(mimeType));
			guid = guid.substring(0,pos);
		}
		pos = name.lastIndexOf(Global.attachmentNameDelimeter);
		if (pos > -1) {
			guid = name.substring(0, pos);
			fd.selectFile(name.substring(pos+Global.attachmentNameDelimeter.length()));		
		}
		if (fd.exec() != 0 && fd.selectedFiles().size() > 0) {
			Resource resBinary = conn.getNoteTable().noteResourceTable.getNoteResource(guid, true);
			String fileName = fd.selectedFiles().get(0);
			QFile saveFile = new QFile(fileName);
			QFile.OpenMode mode = new QFile.OpenMode();
			mode.set(QFile.OpenModeFlag.WriteOnly);
			saveFile.open(mode);
			QDataStream saveOut = new QDataStream(saveFile);
			QByteArray binData = new QByteArray(resBinary.getData().getBody());
			saveOut.writeBytes(binData.toByteArray());
			saveFile.close();
		}
	}

	
	// *************************************************************
	// * decrypt any hidden text.  We could do an XML parse, but 
	// * it is quicker here just to scan for an <img tag & do the fix
	// * the manual way
	// *************************************************************
	private void removeEncryption(String id, String plainText, boolean permanent, String slot) {
		if (!permanent) {
			plainText = " <en-crypt-temp slot=\""+slot  +"\">" +plainText+"</en-crypt-temp> ";
		}
		
		String html = browser.page().mainFrame().toHtml();
		String text = html;
		int imagePos = html.indexOf("<img");
		int endPos;
		for ( ;imagePos>0; ) {
			// Find the end tag
			endPos = text.indexOf(">", imagePos);
			String tag = text.substring(imagePos-1,endPos);
			if (tag.indexOf("id=\""+id+"\"") > -1) {
					text = text.substring(0,imagePos) +plainText+text.substring(endPos+1);
										
					browser.setContent(new QByteArray(text));
					contentChanged();
			}
			imagePos = text.indexOf("<img", imagePos+1);
		}
	}
	
	
	//****************************************************************
	//* Focus shortcuts
	//****************************************************************
	@SuppressWarnings("unused")
	private void focusTitle() {
		titleLabel.setFocus();
	}
	@SuppressWarnings("unused")
	private void focusTag() {
		tagEdit.setFocus();
	}
	@SuppressWarnings("unused")
	private void focusNote() {
		browser.setFocus();
	}
	@SuppressWarnings("unused")
	private void focusAuthor() {
		authorLabel.setFocus();
	}
	@SuppressWarnings("unused")
	private void focusUrl() {
		urlLabel.setFocus();
	}
	

	//*****************************************************************
	//* Set the document background color
	//*****************************************************************
	public void setBackgroundColor(String color) {
		String js = "function changeBackground(color) {"
			+"document.body.style.background = color;"
			+"}" 
			+"changeBackground('" +color+"');";
		browser.page().mainFrame().evaluateJavaScript(js);
		contentChanged();
	}
	
	
	//****************************************************************
	//* MicroFocus changed
	//****************************************************************
	private void microFocusChanged() {
		
		boldButton.setDown(false);
		italicButton.setDown(false);
		underlineButton.setDown(false);
		browser.openAction.setEnabled(false);
		browser.downloadAttachment.setEnabled(false);
		browser.downloadImage.setEnabled(false);
		browser.rotateImageLeft.setEnabled(false);
		browser.rotateImageRight.setEnabled(false);
		browser.insertTableAction.setEnabled(true);
		browser.insertTableRowAction.setEnabled(false);
		browser.deleteTableRowAction.setEnabled(false);
		browser.insertLinkAction.setText("Insert Hyperlink");
		currentHyperlink ="";
		insideList = false;
		forceTextPaste = false;
		
		String js = new String( "function getCursorPos() {"
			+"var cursorPos;"
			+"if (window.getSelection) {"
			+"   var selObj = window.getSelection();"
			+"   var selRange = selObj.getRangeAt(0);"
			+"   var workingNode = window.getSelection().anchorNode.parentNode;"
			+"   while(workingNode != null) { " 
//			+"      window.jambi.printNode(workingNode.nodeName);"
			+"      if (workingNode.nodeName=='EN-CRYPT-TEMP') window.jambi.forceTextPaste();"
			+"      if (workingNode.nodeName=='B') window.jambi.boldActive();"
			+"      if (workingNode.nodeName=='I') window.jambi.italicActive();"
			+"      if (workingNode.nodeName=='U') window.jambi.underlineActive();"
			+"      if (workingNode.nodeName=='UL') window.jambi.setInsideList();"
			+"      if (workingNode.nodeName=='OL') window.jambi.setInsideList();"
			+"      if (workingNode.nodeName=='LI') window.jambi.setInsideList();"
			+"      if (workingNode.nodeName=='TBODY') window.jambi.setInsideTable();"
			+"      if (workingNode.nodeName=='A') {for(var x = 0; x < workingNode.attributes.length; x++ ) {if (workingNode.attributes[x].nodeName.toLowerCase() == 'href') window.jambi.setInsideLink(workingNode.attributes[x].nodeValue);}}"
			+"      if (workingNode.nodeName=='SPAN') {"
			+"         if (workingNode.getAttribute('style') == 'text-decoration: underline;') window.jambi.underlineActive();"
			+"      }"
			+"      workingNode = workingNode.parentNode;"
			+"   }"
			+"}"
			+"} getCursorPos();");
		browser.page().mainFrame().evaluateJavaScript(js);
	}
	
	public void printNode(String n) {
		System.out.println("Node Vaule: " +n);
	}
	
	
	//****************************************************************
	//* Insert a table row
	//****************************************************************
	public void insertTableRow() {
		
		String js = new String( "function insertTableRow() {"
			+"   var selObj = window.getSelection();"
			+"   var selRange = selObj.getRangeAt(0);"
			+"   var workingNode = window.getSelection().anchorNode.parentNode;"
			+"   var cellCount = 0;"
			+"   while(workingNode != null) { " 
			+"      if (workingNode.nodeName.toLowerCase()=='tr') {"
			+"           row = document.createElement('TR');"
			+"           var nodes = workingNode.getElementsByTagName('td');"
			+"           for (j=0; j<nodes.length; j=j+1) {"
			+"              cell = document.createElement('TD');"
			+"              cell.innerHTML='&nbsp;';"
			+"              row.appendChild(cell);"
			+"           }"			
			+"           workingNode.parentNode.insertBefore(row,workingNode.nextSibling);"
			+"           return;"
			+"      }"
			+"      workingNode = workingNode.parentNode;"
			+"   }"
			+"} insertTableRow();");
		browser.page().mainFrame().evaluateJavaScript(js);
		contentChanged();
	}
	//****************************************************************
	//* Insert a table row
	//****************************************************************
	public void deleteTableRow() {
		
		String js = new String( "function deleteTableRow() {"
			+"   var selObj = window.getSelection();"
			+"   var selRange = selObj.getRangeAt(0);"
			+"   var workingNode = window.getSelection().anchorNode.parentNode;"
			+"   var cellCount = 0;"
			+"   while(workingNode != null) { " 
			+"      if (workingNode.nodeName.toLowerCase()=='tr') {"
			+"           workingNode.parentNode.removeChild(workingNode);"
			+"           return;"
			+"      }"
			+"      workingNode = workingNode.parentNode;"
			+"   }"
			+"} deleteTableRow();");
		browser.page().mainFrame().evaluateJavaScript(js);
		contentChanged();
	}
	public void setInsideTable() {
		browser.insertTableRowAction.setEnabled(true);
		browser.deleteTableRowAction.setEnabled(true);
		browser.insertTableAction.setEnabled(false);
		browser.encryptAction.setEnabled(false);
	}
	
	public void setInsideLink(String link) {
		browser.insertLinkAction.setText("Edit Hyperlink");
		currentHyperlink = link;
	}
	
	public void italicActive() {
		italicButton.setDown(true);
	}
	public void boldActive() {
		boldButton.setDown(true);
	}
	public void underlineActive() {
		underlineButton.setDown(true);
	}
	public void forceTextPaste() {
		forceTextPaste = true;
	}
	public void imageContextMenu(String f) {
		browser.downloadImage.setEnabled(true);
		browser.rotateImageRight.setEnabled(true);
		browser.rotateImageLeft.setEnabled(true);
		browser.openAction.setEnabled(true);
		selectedFile = f;
	}
	public void rotateImageRight() {
		QWebSettings.setMaximumPagesInCache(0);
		QWebSettings.setObjectCacheCapacities(0, 0, 0);
		QImage image = new QImage(selectedFile);
		QMatrix matrix = new QMatrix();
		matrix.rotate( 90.0 );
		image = image.transformed(matrix);
		image.save(selectedFile);
		String html = getContent();
		browser.setContent(new QByteArray());
		browser.reload();
		browser.setContent(new QByteArray(html));
		browser.triggerPageAction(WebAction.Reload);
		contentChanged();
		resourceSignal.contentChanged.emit(selectedFile);

	}
	public void rotateImageLeft() {
		QImage image = new QImage(selectedFile);
		QMatrix matrix = new QMatrix();
		matrix.rotate( -90.0 );
		image = image.transformed(matrix);
		image.save(selectedFile);
		String html = getContent();
		browser.setContent(new QByteArray());
		browser.reload();
		browser.setContent(new QByteArray(html));
		browser.triggerPageAction(WebAction.Reload);
		contentChanged();
		resourceSignal.contentChanged.emit(selectedFile);
	}
	public void resourceContextMenu(String f) {
		browser.downloadAttachment.setEnabled(true);
		browser.openAction.setEnabled(true);
		selectedFile = f;
	}
	
	
	//****************************************************************
	//* Apply CSS style to specified word
	//****************************************************************
/*	public void applyStyleToWords(String word, String style) {
		QFile script = new QFile("D:\\NeverNote\\js\\hilight1.js");
		script.open(OpenModeFlag.ReadOnly);
		String s = script.readAll().toString();
		String js = new String(s +" findit('"+word+"', '"+style+"');");
		browser.page().mainFrame().evaluateJavaScript(js);
		System.out.println(getContent());
	}
*/	
	//****************************************************************
	//* Someone tried to paste a resource between notes, so we need  *
	//* to do some special handling.                                 *
	//****************************************************************
	private String fixInternotePaste(String text) {
		logger.log(logger.EXTREME, "Fixing internote paste");
		String returnValue = fixInternotePasteSearch(text, "<img", "src=\"");
		return fixInternotePasteSearch(returnValue, "<a", "href=\"nnres://");
	}
	private String fixInternotePasteSearch(String text, String type, String locTag) {
		
		// First, let's fix the images.
		int startPos = text.indexOf(type);
		int endPos;
		for (; startPos>=0;) {
			endPos = text.indexOf(">", startPos+1);
			String segment = text.substring(startPos, endPos);
			if (segment.indexOf("en-tag") > -1) {
				String newSegment = segment;
				
				int guidStartPos = segment.indexOf("guid=\"");
				int guidEndPos = segment.indexOf("\"", guidStartPos+7);
				String guid = segment.substring(guidStartPos+6,guidEndPos);
				
				Calendar currentTime = new GregorianCalendar();
				Long l = new Long(currentTime.getTimeInMillis());
				long prevTime = l;
				while (l==prevTime) {
					currentTime = new GregorianCalendar();
					l= new Long(currentTime.getTimeInMillis());
				}
				
				Resource r = conn.getNoteTable().noteResourceTable.getNoteResource(guid, true);
				if (r==null)
					return "";
		    	String randint = new String(Long.toString(l));
		    	String extension = null;
		    	if (r.getMime()!= null) {
		    		extension = r.getMime().toLowerCase();
		    		if (extension.indexOf("/")>-1)
		    			extension = extension.substring(extension.indexOf("/")+1);
		    	}
		    	String newFile = randint;
		    	if (r.getAttributes().getFileName() != null && r.getAttributes().getFileName() != "")
		    		if (!locTag.startsWith("src"))
		    			newFile = newFile+Global.attachmentNameDelimeter+r.getAttributes().getFileName();
		    	r.setNoteGuid(currentNote.getGuid());
		    	
		    	r.setGuid(randint);
		    	conn.getNoteTable().noteResourceTable.saveNoteResource(r, true);
				QFile f = new QFile(Global.getDirectoryPath() +"res/" +newFile);
				QByteArray bin = new QByteArray(r.getData().getBody());
				f.open(QFile.OpenModeFlag.WriteOnly);
				f.write(bin);
				f.close();
				newSegment = newSegment.replace("guid=\""+guid, "guid=\""+randint);
				currentNote.getResources().add(r);
				
				int startSrcPos = newSegment.indexOf(locTag);
				int endSrcPos = newSegment.indexOf("\"",startSrcPos+locTag.length()+1);
				String source; 
				if (locTag.startsWith("src")) {
					 source = newSegment.substring(startSrcPos+locTag.length(),endSrcPos);
					newSegment = newSegment.replace(source, Global.getDirectoryPath().replace("\\", "/")+"res/"+newFile);
				} else {
					source = newSegment.substring(startSrcPos+locTag.length(),endSrcPos);
					newSegment = newSegment.replace(source, newFile);
				}
				
				text = text.substring(0,startPos) + newSegment + text.substring(endPos);
			}
			startPos = text.indexOf(type, startPos+1);
		}
		return text;
	}
}