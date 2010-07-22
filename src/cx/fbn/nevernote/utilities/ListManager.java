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

package cx.fbn.nevernote.utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.SavedSearch;
import com.evernote.edam.type.Tag;
import com.trolltech.qt.QThread;
import com.trolltech.qt.core.QDateTime;
import com.trolltech.qt.sql.QSqlQuery;
import com.trolltech.qt.xml.QDomAttr;
import com.trolltech.qt.xml.QDomDocument;
import com.trolltech.qt.xml.QDomElement;
import com.trolltech.qt.xml.QDomNodeList;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.filters.EnSearch;
import cx.fbn.nevernote.filters.NotebookCounter;
import cx.fbn.nevernote.filters.TagCounter;
import cx.fbn.nevernote.signals.NotebookSignal;
import cx.fbn.nevernote.signals.StatusSignal;
import cx.fbn.nevernote.signals.TagSignal;
import cx.fbn.nevernote.signals.ThreadSignal;
import cx.fbn.nevernote.signals.TrashSignal;
import cx.fbn.nevernote.sql.DatabaseConnection;
import cx.fbn.nevernote.threads.CounterRunner;
import cx.fbn.nevernote.threads.SaveRunner;


public class ListManager  {

	
	private final ApplicationLogger logger;  
	DatabaseConnection				conn;
	QSqlQuery						deleteWords;
	QSqlQuery						insertWords;
	
	private List<Tag>				tagIndex;
	private List<Notebook>			notebookIndex;
	private List<Notebook>			archiveNotebookIndex;
	private List<String>			localNotebookIndex;
	private List<Note>				noteIndex;
	private List<SavedSearch>		searchIndex;

	private List<String>			selectedNotebooks;
	
	private List<Note>				masterNoteIndex;
	private List<String>			unsynchronizedNotes;
	private List<String>			selectedTags;
	private String					selectedSearch;
	ThreadSignal					signals;
	public StatusSignal				status;
	private final CounterRunner		notebookCounterRunner;
	private final QThread			notebookThread;
	private final CounterRunner		tagCounterRunner;
	private final QThread			tagThread;
	
	private final CounterRunner		trashCounterRunner;
	private final QThread					trashThread;
	public TrashSignal				trashSignal;
	public HashMap<String,Integer>	titleColors;
	
	private List<NotebookCounter>	notebookCounter;				// count of displayed notes in each notebook
	private List<TagCounter>		tagCounter;						// count of displayed notes for each tag
	
	private EnSearch				enSearch;
	private boolean 				enSearchChanged;
	public HashMap<String, String>	wordMap;
	public TagSignal 				tagSignal;
	public NotebookSignal			notebookSignal;
	private int						trashCount;
	private final int id;
    SaveRunner			saveRunner;					// Thread used to save content.  Used because the xml conversion is slowwwww
    QThread				saveThread;
	
	// Constructor
 	public ListManager(DatabaseConnection d, ApplicationLogger l, int instance_id) {
 		conn = d;
 		logger = l;
 		id = instance_id;
 		
    	status = new StatusSignal();
		signals = new ThreadSignal();
		
		// setup index locks
		enSearchChanged = false;
		
		// Setup arrays
		masterNoteIndex = null;
		selectedTags = new ArrayList<String>();

		notebookCounter = new ArrayList<NotebookCounter>();
		tagCounter = new ArrayList<TagCounter>();
		selectedNotebooks = new ArrayList<String>();
		unsynchronizedNotes = new ArrayList<String>();
		
		reloadIndexes();
		
 		notebookSignal = new NotebookSignal();
 		notebookCounterRunner = new CounterRunner("notebook_counter.log", CounterRunner.NOTEBOOK, Global.getDatabaseUrl(), Global.getDatabaseUserid(), Global.getDatabaseUserPassword(), Global.cipherPassword);
 		notebookCounterRunner.setNoteIndex(getNoteIndex());
 		notebookCounterRunner.notebookSignal.countsChanged.connect(this, "setNotebookCounter(List)");
		notebookThread = new QThread(notebookCounterRunner, "Notebook Counter Thread");
		notebookThread.start();
		
 		tagSignal = new TagSignal();
 		tagCounterRunner = new CounterRunner("tag_counter.log", CounterRunner.TAG, Global.getDatabaseUrl(), Global.getDatabaseUserid(), Global.getDatabaseUserPassword(), Global.cipherPassword);
 		tagCounterRunner.setNoteIndex(getNoteIndex());
 		tagCounterRunner.tagSignal.countsChanged.connect(this, "setTagCounter(List)");
		tagThread = new QThread(tagCounterRunner, "Tag Counter Thread");
		tagThread.start();
		
 		trashSignal = new TrashSignal();
 		trashCounterRunner = new CounterRunner("trash_counter.log", CounterRunner.TRASH, Global.getDatabaseUrl(), Global.getDatabaseUserid(), Global.getDatabaseUserPassword(), Global.cipherPassword);
 		trashCounterRunner.trashSignal.countChanged.connect(this, "trashSignalReceiver(Integer)");
		trashThread = new QThread(trashCounterRunner, "Trash Counter Thread");
		trashThread.start();
 		reloadTrashCount();
 		
		wordMap = new HashMap<String, String>();
		tagSignal = new TagSignal();
		
		logger.log(logger.EXTREME, "Setting save thread");
		saveRunner = new SaveRunner("saveRunner.log", Global.getDatabaseUrl(), Global.getDatabaseUserid(), Global.getDatabaseUserPassword(), Global.cipherPassword);
		saveThread = new QThread(saveRunner, "Save Runner Thread");
		saveThread.start();

		loadNoteTitleColors();
				
	}
 	
 	public void stop() {
 		saveRunner.addWork("stop", "");
 		tagCounterRunner.release(CounterRunner.EXIT);
 		notebookCounterRunner.release(CounterRunner.EXIT);
 		trashCounterRunner.release(CounterRunner.EXIT);
 		
 		logger.log(logger.MEDIUM, "Waiting for notebookCounterThread to stop"); 			
		try {
			notebookThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
 		}
		
 		logger.log(logger.MEDIUM, "Waiting for tagCounterThread to stop"); 			
		try {
			tagThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
 		}

 		logger.log(logger.MEDIUM, "Waiting for trashThread to stop"); 			
		try {
			trashThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
 		}


		logger.log(logger.MEDIUM, "Waiting for saveThread to stop"); 			
		try {
			saveThread.join(0);
		} catch (InterruptedException e) {
			e.printStackTrace();
 		}


 	}

 	//***************************************************************
 	//***************************************************************
 	//* Refresh lists after a db sync
 	//***************************************************************
 	//***************************************************************
 	public void refreshLists(Note n, boolean dirty, String content) {
 		if (dirty) {
// 			conn.getNoteTable().updateNoteContent(n.getGuid(), n.getContent());
 			saveRunner.addWork(n.getGuid(), content);
 			conn.getNoteTable().updateNoteTitle(n.getGuid(), n.getTitle());
 		}
 		
 		setSavedSearchIndex(conn.getSavedSearchTable().getAll());
 		setTagIndex(conn.getTagTable().getAll());
 		setNotebookIndex(conn.getNotebookTable().getAll());
 		
 		List<Notebook> local = conn.getNotebookTable().getAllLocal();
 		localNotebookIndex = new ArrayList<String>();
 		for (int i=0; i<local.size(); i++)
 			localNotebookIndex.add(local.get(i).getGuid());
 		
		masterNoteIndex = conn.getNoteTable().getAllNotes();
		// For performance reasons, we didn't get the tags for every note individually.  We now need to 
		// get them
		List<cx.fbn.nevernote.sql.NoteTagsRecord> noteTags = conn.getNoteTable().noteTagsTable.getAllNoteTags();
 		for (int i=0; i<masterNoteIndex.size(); i++) {
			List<String> tags = new ArrayList<String>();
			List<String> names = new ArrayList<String>();
			for (int j=0; j<noteTags.size(); j++) {
				if (masterNoteIndex.get(i).getGuid().equals(noteTags.get(j).noteGuid)) {
					tags.add(noteTags.get(j).tagGuid);
					names.add(getTagNameByGuid(noteTags.get(j).tagGuid));
				}
			}
			
			masterNoteIndex.get(i).setTagGuids(tags);
			masterNoteIndex.get(i).setTagNames(names);
		}
		
		
		setUnsynchronizedNotes(conn.getNoteTable().getUnsynchronizedGUIDs());
		
		enSearchChanged = true;
 	}

 	public void reloadIndexes() {
		setUnsynchronizedNotes(conn.getNoteTable().getUnsynchronizedGUIDs());

 		List<Notebook> local = conn.getNotebookTable().getAllLocal();
 		localNotebookIndex = new ArrayList<String>();
 		for (int i=0; i<local.size(); i++)
 			localNotebookIndex.add(local.get(i).getGuid());
 		
 		// Load tags
		setTagIndex(conn.getTagTable().getAll());
		// Load notebooks
		setNotebookIndex(conn.getNotebookTable().getAll());
		// load archived notebooks (if note using the EN interface)
		setArchiveNotebookIndex(conn.getNotebookTable().getAllArchived());
		// load saved search index
		setSavedSearchIndex(conn.getSavedSearchTable().getAll());
		// Load search helper utility
		enSearch = new EnSearch(conn,  logger, "", getTagIndex(), Global.getMinimumWordLength(), Global.getRecognitionWeight());
		logger.log(logger.HIGH, "Building note index");

		if (masterNoteIndex == null) { 
			masterNoteIndex = conn.getNoteTable().getAllNotes();
		}
		// For performance reasons, we didn't get the tags for every note individually.  We now need to 
		// get them
		List<cx.fbn.nevernote.sql.NoteTagsRecord> noteTags = conn.getNoteTable().noteTagsTable.getAllNoteTags();
 		for (int i=0; i<masterNoteIndex.size(); i++) {
			List<String> tags = new ArrayList<String>();
			List<String> names = new ArrayList<String>();
			for (int j=0; j<noteTags.size(); j++) {
				if (masterNoteIndex.get(i).getGuid().equals(noteTags.get(j).noteGuid)) {
					tags.add(noteTags.get(j).tagGuid);
					names.add(getTagNameByGuid(noteTags.get(j).tagGuid));
				}
			}
			
			masterNoteIndex.get(i).setTagGuids(tags);
			masterNoteIndex.get(i).setTagNames(names);
		}
 		
 		setNoteIndex(masterNoteIndex);

 	}
 	
	//***************************************************************
	//***************************************************************
	//* selected notebooks
	//***************************************************************
	//***************************************************************
	// Return the selected notebook(s)
	public List<String> getSelectedNotebooks() {
		return selectedNotebooks;
	}
	// Set the current selected notebook(s)
	public void setSelectedNotebooks(List <String> s) {
		if (s == null) 
			s = new ArrayList<String>();
		selectedNotebooks = s;
	}
	
				
    //***************************************************************
    //***************************************************************
    //** These functions deal with setting & retrieving the master lists
    //***************************************************************
    //***************************************************************
	// save the saved search index
	private void setSavedSearchIndex(List<SavedSearch> t) {
		searchIndex = t;
	}
	// Retrieve the Tag index
	public List<SavedSearch> getSavedSearchIndex() {
		return searchIndex;

	}
	// save the tag index
	private void setTagIndex(List<Tag> t) {
		tagIndex = t;
	}	
	// Retrieve the Tag index
	public List<Tag> getTagIndex() {
		return tagIndex;
	}
	private void setNotebookIndex(List<Notebook> t) {
		notebookIndex = t;
	}
	private void setArchiveNotebookIndex(List<Notebook> t) {
		archiveNotebookIndex = t;
	}
	// Retrieve the Notebook index
	public List<Notebook> getNotebookIndex() {
		return notebookIndex;

	}
	public List<Notebook> getArchiveNotebookIndex() {
		return archiveNotebookIndex;
	}
	// Save the current note list
	private void setNoteIndex(List<Note> n) {
		noteIndex = n;
	}
	// Get the note index
	public synchronized List<Note> getNoteIndex() {
		return noteIndex;
	}
	// Save the count of notes per notebook
	public void setNotebookCounter(List<NotebookCounter> n) {
		notebookCounter = n;
		notebookSignal.refreshNotebookTreeCounts.emit(getNotebookIndex(), notebookCounter);
	}
	public List<NotebookCounter> getNotebookCounter() {
		return notebookCounter;
	}
	// Save the count of notes for each tag
	public void setTagCounter(List<TagCounter> n) {
		tagCounter = n;
		tagSignal.refreshTagTreeCounts.emit(tagCounter);
	}
	public List<TagCounter> getTagCounter() {
		return tagCounter;
	}
	public List<String> getLocalNotebooks() {
		return localNotebookIndex;
	}
	// Unsynchronized Note List
	public List<String> getUnsynchronizedNotes() {
		return unsynchronizedNotes;
	}
	public void setUnsynchronizedNotes(List<String> l) {
		unsynchronizedNotes = l;
	}
	// Return a count of items in the trash
	public int getTrashCount() {
		return trashCount;
	}
	// get the EnSearch variable
	public EnSearch getEnSearch() {
		return enSearch;
	}
	public List<Note> getMasterNoteIndex() {
		return masterNoteIndex;
	}
	
    //***************************************************************
    //***************************************************************
    //** These functions deal with setting & retrieving filters
    //***************************************************************
    //***************************************************************
	public void setEnSearch(String t) {
		enSearch = new EnSearch(conn,logger, t, getTagIndex(), Global.getMinimumWordLength(), Global.getRecognitionWeight());
		enSearchChanged = true;
	}
	// Save search tags
	public void setSelectedTags(List<String> selectedTags) {
		this.selectedTags = selectedTags;
	}
	// Save seleceted search
	public void setSelectedSavedSearch(String s) {
		this.selectedSearch = s;
	}
	// Get search tags
	public List<String> getSelectedTags() {
		return selectedTags;
	}
	// Get saved search
	public String getSelectedSearch() {
		return selectedSearch;
	}
	
	
	
	
    //***************************************************************
    //***************************************************************
    //** Note functions
    //***************************************************************
    //***************************************************************
	// Save Note Tags
	public void saveNoteTags(String noteGuid, List<String> tags) {
		logger.log(logger.HIGH, "Entering ListManager.saveNoteTags");
		String tagName;
		conn.getNoteTable().noteTagsTable.deleteNoteTag(noteGuid);
		List<String> tagGuids = new ArrayList<String>();
		boolean newTagCreated = false;
		
		for (int i=0; i<tags.size(); i++) {
			tagName = tags.get(i);
			boolean found = false;
			for (int j=0; j<tagIndex.size(); j++) {
				if (tagIndex.get(j).getName().equalsIgnoreCase(tagName)) {
					conn.getNoteTable().noteTagsTable.saveNoteTag(noteGuid, tagIndex.get(j).getGuid());
					tagGuids.add(tagIndex.get(j).getGuid());
					j=tagIndex.size()+1;
					found = true;
				}
			}
			if (!found) {
				Tag nTag = new Tag();
				nTag.setName(tagName);
				Calendar currentTime = new GregorianCalendar();
				Long l = new Long(currentTime.getTimeInMillis());
				long prevTime = l;
				while (l==prevTime) {
					currentTime = new GregorianCalendar();
					l=currentTime.getTimeInMillis();
				}
				String randint = new String(Long.toString(l));
			
				nTag.setUpdateSequenceNum(0);
				nTag.setGuid(randint);
				conn.getTagTable().addTag(nTag, true);
				getTagIndex().add(nTag);
				conn.getNoteTable().noteTagsTable.saveNoteTag(noteGuid, nTag.getGuid());
				tagGuids.add(nTag.getGuid());
				newTagCreated = true;
			}
		}
		
		for (int i=0; i<noteIndex.size(); i++) {
			if (noteIndex.get(i).getGuid().equals(noteGuid)) {
				noteIndex.get(i).setTagNames(tags);
				noteIndex.get(i).setTagGuids(tagGuids);
				i=noteIndex.size()+1;
			}
		}
		if (newTagCreated)
			tagSignal.listChanged.emit();
		logger.log(logger.HIGH, "Leaving ListManager.saveNoteTags");
	}
	// Delete a note
	public void deleteNote(String guid) {
		trashCounterRunner.abortCount = true;
		Calendar currentTime = new GregorianCalendar();
		Long l = new Long(currentTime.getTimeInMillis());
		long prevTime = l;
		while (l==prevTime) {
			currentTime = new GregorianCalendar();
			l=currentTime.getTimeInMillis();
		}
		
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setActive(false);
				masterNoteIndex.get(i).setDeleted(l);
				i=masterNoteIndex.size();
			}
		}
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setActive(false);
				getNoteIndex().get(i).setDeleted(l);
				i=getNoteIndex().size();
			}
		}
		conn.getNoteTable().deleteNote(guid);
		reloadTrashCount();
	}
	// Delete a note
	public void restoreNote(String guid) {
		trashCounterRunner.abortCount = true;
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setActive(true);
				masterNoteIndex.get(i).setDeleted(0);
				i=masterNoteIndex.size();
			}
		}
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setActive(true);
				getNoteIndex().get(i).setDeleted(0);
				i=getNoteIndex().size();
			}
		}
		conn.getNoteTable().restoreNote(guid);
		reloadTrashCount();
	}
	public void updateNote(Note n) {
		
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(n.getGuid())) {
				masterNoteIndex.remove(i);
				masterNoteIndex.add(n);
			}
		}
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(n.getGuid())) {
				getNoteIndex().get(i).setActive(true);
				getNoteIndex().get(i).setDeleted(0);
				i=getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNote(n, true);
	}
	// Add a note.  
	public void addNote(Note n) {
		getNoteIndex().add(n);
		masterNoteIndex.add(n);
	}
	// Expunge a note
	public void expungeNote(String guid) {
		trashCounterRunner.abortCount = true;
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.remove(i);
				i=masterNoteIndex.size();
			}
		}
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().remove(i);
				i=getNoteIndex().size();
			}
		}
		conn.getNoteTable().expungeNote(guid, false, true);
		reloadTrashCount();
	}
	// Expunge a note
	public void emptyTrash() {
		trashCounterRunner.abortCount = true;		
		for (int i=masterNoteIndex.size()-1; i>=0; i--) {
			if (!masterNoteIndex.get(i).isActive()) {
				masterNoteIndex.remove(i);
			}
		}
		
		for (int i=getNoteIndex().size()-1; i>=0; i--) {
			if (!getNoteIndex().get(i).isActive()) {
				getNoteIndex().remove(i);
			} 
		}

		conn.getNoteTable().expungeAllDeletedNotes();
		reloadTrashCount();
	}
	// The trash counter thread has produced a result
	@SuppressWarnings("unused")
	private void trashSignalReceiver(Integer i) {
		trashCount = i;
		trashSignal.countChanged.emit(i);
	}
	// Update note contents
	public void updateNoteContent(String guid, String content) {
		logger.log(logger.HIGH, "Entering ListManager.updateNoteContent");
//		EnmlConverter enml = new EnmlConverter(logger);
//		String text = enml.convert(guid, content);
		
		// Update the list tables 
/*		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setContent(text);
				i = masterNoteIndex.size();
			}
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setContent(text);
				i = getNoteIndex().size();
			}
		}
*/		
		// Check if any new tags were encountered
/*		if (enml.saveInvalidXML) {
			List<String> elements = Global.invalidElements;
			for (int i=0; i<elements.size(); i++) {
				conn.getInvalidXMLTable().addInvalidElement(elements.get(i));
			}
			for (String key : Global.invalidAttributes.keySet()) {
				ArrayList<String> attributes = Global.invalidAttributes.get(key);
				for (int i=0; i<attributes.size(); i++) {
					conn.getInvalidXMLTable().addInvalidAttribute(key, attributes.get(i));
				}
			}
		}
*/
		saveRunner.addWork(guid, content);
//		conn.getNoteTable().updateNoteContent(guid, content);
		logger.log(logger.HIGH, "Leaving ListManager.updateNoteContent");
	}
	// Update a note creation date
	public void updateNoteCreatedDate(String guid, QDateTime date) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setCreated(date.toTime_t()*1000);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setCreated(date.toTime_t()*1000);
				i = getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNoteCreatedDate(guid, date);
	}
	// Subject date has been changed
	public void updateNoteSubjectDate(String guid, QDateTime date) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).getAttributes().setSubjectDate(date.toTime_t()*1000);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setCreated(date.toTime_t()*1000);
				i = getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNoteSubjectDate(guid, date);
	}
	// Author has changed
	public void updateNoteAuthor(String guid, String author) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).getAttributes().setAuthor(author);
				masterNoteIndex.get(i).getAttributes().setAltitudeIsSet(true);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).getAttributes().setAuthor(author);
				getNoteIndex().get(i).getAttributes().setAuthorIsSet(true);
				i = getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNoteAuthor(guid, author);
	}
	// Author has changed
	public void updateNoteGeoTag(String guid, Double lon, Double lat, Double alt) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).getAttributes().setLongitude(lon);
				masterNoteIndex.get(i).getAttributes().setLongitudeIsSet(true);
				masterNoteIndex.get(i).getAttributes().setLatitude(lat);
				masterNoteIndex.get(i).getAttributes().setLatitudeIsSet(true);
				masterNoteIndex.get(i).getAttributes().setAltitude(alt);
				masterNoteIndex.get(i).getAttributes().setAltitudeIsSet(true);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).getAttributes().setLongitude(lon);
				getNoteIndex().get(i).getAttributes().setLongitudeIsSet(true);
				getNoteIndex().get(i).getAttributes().setLatitude(lat);
				getNoteIndex().get(i).getAttributes().setLatitudeIsSet(true);
				getNoteIndex().get(i).getAttributes().setAltitude(alt);
				getNoteIndex().get(i).getAttributes().setAltitudeIsSet(true);
				i = getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNoteGeoTags(guid, lon, lat, alt);
	}
	// Author has changed
	public void updateNoteSourceUrl(String guid, String url) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).getAttributes().setSourceURL(url);
				masterNoteIndex.get(i).getAttributes().setSourceURLIsSet(true);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).getAttributes().setSourceURL(url);
				getNoteIndex().get(i).getAttributes().setSourceURLIsSet(true);
				i = getNoteIndex().size();
			}
		}
		conn.getNoteTable().updateNoteSourceUrl(guid, url);
	}
	// Update a note last changed date
	public void updateNoteAlteredDate(String guid, QDateTime date) {
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setUpdated(date.toTime_t()*1000);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setUpdated(date.toTime_t()*1000);
				i = getNoteIndex().size();
			}
		}
		
		conn.getNoteTable().updateNoteAlteredDate(guid, date);
	}
	// Update a note title
	public void updateNoteTitle(String guid, String title) {
		logger.log(logger.HIGH, "Entering ListManager.updateNoteTitle");
		conn.getNoteTable().updateNoteTitle(guid, title);
		
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setTitle(title);
				i = masterNoteIndex.size();
			}	
		}
		// Update the list tables 
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setTitle(title);
				i = getNoteIndex().size();
			}
		}
		
		logger.log(logger.HIGH, "Leaving ListManager.updateNoteTitle");
	}
	// Update a note's notebook
	public void updateNoteNotebook(String guid, String notebookGuid) {
		logger.log(logger.HIGH, "Entering ListManager.updateNoteNotebook");
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid().equals(guid)) {
				masterNoteIndex.get(i).setNotebookGuid(notebookGuid);
				i=masterNoteIndex.size();
			}
		}
		for (int i=0; i<getNoteIndex().size(); i++) {
			if (getNoteIndex().get(i).getGuid().equals(guid)) {
				getNoteIndex().get(i).setNotebookGuid(notebookGuid);
				i=masterNoteIndex.size();
			}
		}
		conn.getNoteTable().updateNoteNotebook(guid, notebookGuid, true);
		logger.log(logger.HIGH, "Leaving ListManager.updateNoteNotebook");
	}
	// Update a note sequence number
	public void updateNoteSequence(String guid, int sequence) {
		logger.log(logger.HIGH, "Entering ListManager.updateNoteSequence");

		conn.getNoteTable().updateNoteSequence(guid, sequence);
		
		for (int i=0; i<noteIndex.size(); i++) {
			if (noteIndex.get(i).getGuid().equals(guid)) {
				noteIndex.get(i).setUpdateSequenceNum(sequence);
				i=noteIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateNoteSequence");
	}
	public void updateNoteGuid(String oldGuid, String newGuid, boolean updateDatabase) {
		logger.log(logger.HIGH, "Entering ListManager.updateNoteGuid");
		
		if (updateDatabase) 
			conn.getNoteTable().updateNoteGuid(oldGuid, newGuid);
		
		for (int i=0; i<masterNoteIndex.size(); i++) {
			if (masterNoteIndex.get(i).getGuid() != null && masterNoteIndex.get(i).getGuid().equals(oldGuid)) {
				masterNoteIndex.get(i).setGuid(newGuid);
				i=masterNoteIndex.size()+1;
			}
		}
		for (int i=0; i<noteIndex.size(); i++) {
			if (noteIndex.get(i).getGuid() != null && noteIndex.get(i).getGuid().equals(oldGuid)) {
				noteIndex.get(i).setGuid(newGuid);
				i=noteIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateNoteGuid");

	}

	
	//************************************************************************************
	//************************************************************************************
	//**  Tag functions
	//************************************************************************************
	//************************************************************************************	
	// Update a tag sequence number
	public void updateTagSequence(String guid, int sequence) {
		logger.log(logger.HIGH, "Entering ListManager.updateTagSequence");

		conn.getTagTable().updateTagSequence(guid, sequence);	
		for (int i=0; i<tagIndex.size(); i++) {
			if (tagIndex.get(i).getGuid().equals(guid)) {
				getTagIndex().get(i).setUpdateSequenceNum(sequence);
				i=tagIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateTagSequence");
	}
	// Update a tag guid number
	public void updateTagGuid(String oldGuid, String newGuid) {
		logger.log(logger.HIGH, "Entering ListManager.updateTagGuid");

		conn.getTagTable().updateTagGuid(oldGuid, newGuid);	
		for (int i=0; i<tagIndex.size(); i++) {
			if (tagIndex.get(i).getGuid().equals(oldGuid)) {
				tagIndex.get(i).setGuid(newGuid);
				i=tagIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateTagGuid");

	}
	// Count tag results
//	@SuppressWarnings("unused")
//	private void reloadTagCount() {
//		tagCounterRunner.threadLock.lock(); 
//		tagCounterRunner.setNoteIndex(getNoteIndex());
//		QThreadPool.globalInstance().tryStart(tagCounterRunner);
//		tagCounterRunner.threadLock.unlock();
//	}

	//************************************************************************************
	//************************************************************************************
	//**  Notebook functions
	//************************************************************************************
	//************************************************************************************	
	// Delete a notebook
	public void deleteNotebook(String guid) {
		for (int i=0; i<getNotebookIndex().size(); i++) {
			if (getNotebookIndex().get(i).getGuid().equals(guid)) {
				getNotebookIndex().remove(i);
				i=masterNoteIndex.size();
			}
		}
		conn.getNotebookTable().expungeNotebook(guid, true);		
	}
	// Update a notebook sequence number
	public void updateNotebookSequence(String guid, int sequence) {
		logger.log(logger.HIGH, "Entering ListManager.updateNotebookSequence");

		conn.getNotebookTable().updateNotebookSequence(guid, sequence);
		
		for (int i=0; i<notebookIndex.size(); i++) {
			if (notebookIndex.get(i).getGuid().equals(guid)) {
				notebookIndex.get(i).setUpdateSequenceNum(sequence);
				i=notebookIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateNotebookSequence");

	}
	// Update a notebook Guid number
	public void updateNotebookGuid(String oldGuid, String newGuid) {
		logger.log(logger.HIGH, "Entering ListManager.updateNotebookGuid");

		conn.getNotebookTable().updateNotebookGuid(oldGuid, newGuid);
		
		for (int i=0; i<notebookIndex.size(); i++) {
			if (notebookIndex.get(i).getGuid().equals(oldGuid)) {
				notebookIndex.get(i).setGuid(newGuid);
				i=notebookIndex.size()+1;
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.updateNotebookGuid");

	}
	
	
	//************************************************************************************
	//************************************************************************************
	//**  Load and filter the note index
	//************************************************************************************
	//************************************************************************************
//	public void clearNoteIndexSearch() {
//		setNoteIndex(masterNoteIndex);
//	}
	// Load the note index based upon what the user wants.
	public void loadNotesIndex() {
		logger.log(logger.EXTREME, "Entering ListManager.loadNotesIndex()");
		tagCounterRunner.abortCount = true;
		notebookCounterRunner.abortCount = true;
		trashCounterRunner.abortCount = true;
		
		List<Note> index = new ArrayList<Note>();
		
		List<Note> matches;
		if (enSearchChanged || masterNoteIndex == null)
			matches = enSearch.matchWords();
		else
			matches = masterNoteIndex;
		
		if (matches == null)
			matches = masterNoteIndex;
		
		for (int i=0; i<matches.size(); i++) {
			Note n = matches.get(i);
			boolean goodNotebook = false;
			boolean goodTag = false;
			boolean goodAttribute = false;
			boolean goodStatus = false;
			
			// Check note status
			if (!n.isActive() && Global.showDeleted)
				index.add(n);
			else {
				if (n.isActive() && !Global.showDeleted)
					goodStatus = true;
				// Begin filtering results
				if (goodStatus)
					goodNotebook = filterByNotebook(n.getNotebookGuid());
				if (goodNotebook) 
					goodTag = filterByTag(n.getTagGuids());
				if (goodTag)
					goodAttribute = conn.getNoteTable().checkAttributeSelection(n);
				if (goodAttribute)
					index.add(n);
			}

		}	
		countNotebookResults(index);
		countTagResults(index);
		enSearchChanged = false;
		setNoteIndex(index);
		reloadTrashCount();
		logger.log(logger.EXTREME, "Leaving ListManager.loadNotesIndex()");
	}
	public void countNotebookResults(List<Note> index) {
		logger.log(logger.EXTREME, "Entering ListManager.countNotebookResults()");
		if (!Global.mimicEvernoteInterface) {
			notebookCounterRunner.setNoteIndex(index);
			notebookCounterRunner.release(CounterRunner.NOTEBOOK);
		} else {
			notebookCounterRunner.setNoteIndex(masterNoteIndex);
			notebookCounterRunner.release(CounterRunner.NOTEBOOK_ALL);
		}
		logger.log(logger.EXTREME, "Leaving ListManager.countNotebookResults()");
	}
	public void countTagResults(List<Note> index) {
		logger.log(logger.EXTREME, "Entering ListManager.countTagResults");
		if (!Global.tagBehavior().equalsIgnoreCase("DoNothing")) {
			tagCounterRunner.setNoteIndex(index);
			tagCounterRunner.release(CounterRunner.TAG);
		} else {
			tagCounterRunner.setNoteIndex(null);
			tagCounterRunner.release(CounterRunner.TAG_ALL);
		}
		logger.log(logger.EXTREME, "Leaving ListManager.countTagResults()");
	}
	// Update the count of items in the trash
	public void reloadTrashCount() {
		logger.log(logger.EXTREME, "Entering ListManager.reloadTrashCount");
		trashCounterRunner.setNoteIndex(getNoteIndex());
		trashCounterRunner.release(CounterRunner.TRASH);
		logger.log(logger.EXTREME, "Leaving ListManager.reloadTrashCount");
	}	
	
	private boolean filterByNotebook(String guid) {
		boolean good = false;
		if (selectedNotebooks.size() == 0)
			good = true;
		if (!good && selectedNotebooks.contains(guid)) 
			good = true;

		for (int i=0; i<getArchiveNotebookIndex().size() && good; i++) {
			if (guid.equals(getArchiveNotebookIndex().get(i).getGuid())) {
				good = false;
				return good;
			}
		}
		return good;
	}
	private boolean filterByTag(List<String> noteTags) {
		if (noteTags == null || selectedTags == null)
			return true;
		
		if (selectedTags.size() == 0) 
			return true;
		
		for (int i=0; i<selectedTags.size(); i++) {
			String selectedGuid = selectedTags.get(i);
			if (noteTags.contains(selectedGuid))
					return true;
		}
		return false;
	}

	
	
	public void updateNoteTitleColor(String guid, Integer color) {
		titleColors.remove(guid);
		titleColors.put(guid, color);
		conn.getNoteTable().setNoteTitleColor(guid, color);
	}
	public void loadNoteTitleColors() {
		List<Pair<String,Integer>> colors = conn.getNoteTable().getNoteTitleColors();
		if (titleColors == null)
			titleColors = new HashMap<String,Integer>();
		else
			titleColors.clear();
		for (int i=0; i<colors.size(); i++) {
			titleColors.put(colors.get(i).getFirst(), colors.get(i).getSecond());
		}
	}
	
	//********************************************************************************
	//********************************************************************************
	//* Support signals from the index thread
	//********************************************************************************
	//********************************************************************************
	// Reset a flag if an index is needed
	public void setIndexNeeded(String guid, String type, Boolean b) {
		if (Global.keepRunning && type.equalsIgnoreCase("content"))
			conn.getNoteTable().setIndexNeeded(guid, false);
		if (Global.keepRunning && type.equalsIgnoreCase("resource")) {
			conn.getNoteTable().noteResourceTable.setIndexNeeded(guid, b);
		}
	}
	
	
	
	public boolean threadCheck(int id) {
		if (id == Global.notebookCounterThreadId) 
			return notebookThread.isAlive();
		if (id == Global.tagCounterThreadId) 
			return tagThread.isAlive();
		if (id == Global.trashCounterThreadId) 
			return trashThread.isAlive();
		if (id == Global.saveThreadId) 
			return saveThread.isAlive();
		return false;
	}
	
	
	
	//********************************************************************************
	//********************************************************************************
	//* Utility Functions
	//********************************************************************************
	//********************************************************************************
	public void compactDatabase() {
		conn.compactDatabase();
//		IndexConnection idx = new IndexConnection(logger, "nevernote-compact");
//		idx.dbSetup();
//		idx.dbShutdown();
	}

	// Rebuild the note HTML to something usable
	public List<String> scanNoteForResources(Note n) {
		logger.log(logger.HIGH, "Entering ListManager.scanNoteForResources");
		logger.log(logger.EXTREME, "Note guid: " +n.getGuid());
		QDomDocument doc = new QDomDocument();
		QDomDocument.Result result = doc.setContent(n.getContent());
		if (!result.success) {
			logger.log(logger.MEDIUM, "Parse error when scanning note for resources.");
			logger.log(logger.MEDIUM, "Note guid: " +n.getGuid());
			return null;
		}
				
		List<String> returnArray = new ArrayList<String>();
		QDomNodeList anchors = doc.elementsByTagName("en-media");
		for (int i=0; i<anchors.length(); i++) {
			QDomElement enmedia = anchors.at(i).toElement();
			if (enmedia.hasAttribute("type")) {
				QDomAttr hash = enmedia.attributeNode("hash");
				returnArray.add(hash.value().toString());
			}
		}
		logger.log(logger.HIGH, "Leaving ListManager.scanNoteForResources");
		return returnArray;
	}
	// Given a list of tags, produce a string list of tag names
	public String getTagNamesForNote(Note n) {
		StringBuffer buffer = new StringBuffer(100);
		Vector<String> v = new Vector<String>();
		List<String> guids = n.getTagGuids();
		
		if (guids == null) 
			return "";
		
		for (int i=0; i<guids.size(); i++) {
			v.add(getTagNameByGuid(guids.get(i)));
		}
		Comparator<String> comparator = Collections.reverseOrder();
		Collections.sort(v,comparator);
		Collections.reverse(v);
		
		for (int i = 0; i<v.size(); i++) {
			if (i>0) 
				buffer.append(", ");
			buffer.append(v.get(i));
		}
		
		return buffer.toString();
	}
	// Get a tag name when given a tag guid
	public String getTagNameByGuid(String guid) {
		for (int i=0; i<getTagIndex().size(); i++) {
			String s = getTagIndex().get(i).getGuid();
			if (s.equals(guid)) { 
				return getTagIndex().get(i).getName();
			}
		}
		return "";
	}
	// For a notebook guid, return the name
	public String getNotebookNameByGuid(String guid) {
		if (notebookIndex == null)
			return null;
		for (int i=0; i<notebookIndex.size(); i++) {
			String s = notebookIndex.get(i).getGuid();
			if (s.equals(guid)) { 
				return notebookIndex.get(i).getName();
			}
		}
		return "";
	}
	
	
}
