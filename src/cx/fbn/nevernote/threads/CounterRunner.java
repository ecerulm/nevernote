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

package cx.fbn.nevernote.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;
import com.trolltech.qt.core.QMutex;
import com.trolltech.qt.core.QObject;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.filters.NotebookCounter;
import cx.fbn.nevernote.filters.TagCounter;
import cx.fbn.nevernote.signals.NotebookSignal;
import cx.fbn.nevernote.signals.TagSignal;
import cx.fbn.nevernote.signals.TrashSignal;
import cx.fbn.nevernote.sql.DatabaseConnection;
import cx.fbn.nevernote.sql.runners.NoteTagsRecord;
import cx.fbn.nevernote.utilities.ApplicationLogger;
import cx.fbn.nevernote.utilities.Pair;

public class CounterRunner extends QObject implements Runnable {
	 
	private final ApplicationLogger 	logger;
	private volatile boolean			keepRunning;
	public int							ID;
	public volatile NotebookSignal		notebookSignal;
	public volatile TrashSignal			trashSignal;
	public volatile TagSignal			tagSignal;
	private volatile Vector<String>		notebookIndex;
	private volatile Vector<String>		noteIndex;
	private volatile Vector<Boolean>	activeIndex;
	public int							type;
	public QMutex						threadLock;
	
	public static int					EXIT=0;
	public static int 					NOTEBOOK=1;
	public static int					TAG=2;
	public static int 					TRASH=3;
	public static int					TAG_ALL = 4;
	public static int					NOTEBOOK_ALL = 5;
	
	public boolean 						ready = false;
	private volatile LinkedBlockingQueue<Integer> readyQueue = new LinkedBlockingQueue<Integer>();
	
	
	//*********************************************
	//* Constructor                               *
	//*********************************************
	public CounterRunner(String logname, int t) {
		type = t;
		threadLock = new QMutex();
		logger = new ApplicationLogger(logname);
//		setAutoDelete(false);	
		keepRunning = true;
		notebookSignal = new NotebookSignal();
		tagSignal = new TagSignal();
		trashSignal = new TrashSignal();
		
		notebookIndex = new Vector<String>();
		activeIndex = new Vector<Boolean>();
		noteIndex = new Vector<String>();
	}
	
	
	
	//*********************************************
	//* Run unit                                  *
	//*********************************************
	@Override
	public void run() {
		boolean keepRunning = true;
		
		thread().setPriority(Thread.MIN_PRIORITY);
		while(keepRunning) {
			ready = true;
			try {
				
				type = readyQueue.take();
				threadLock.lock();
				if (type == EXIT)
					keepRunning = false;
				if (type == NOTEBOOK)
					countNotebookResults();
				if (type == NOTEBOOK_ALL)
					countNotebookResults();
				if (type == TAG)
					countTagResults();
				if (type == TAG_ALL)
					countTagResults();
				if (type == TRASH)
					countTrashResults();
				threadLock.unlock();
			} catch (InterruptedException e) {}
		}
	}
	
	
	
	public void setNoteIndex(List<Note> idx) {
		threadLock.lock();
		notebookIndex.clear();
		activeIndex.clear();
		noteIndex.clear();
		if (idx != null) {
			for (int i=0; i<idx.size(); i++) {
				if (Global.showDeleted && !idx.get(i).isActive()) {
					notebookIndex.add(new String(idx.get(i).getNotebookGuid()));
					noteIndex.add(new String(idx.get(i).getGuid()));
					activeIndex.add(new Boolean(idx.get(i).isActive()));
				}  
				if (!Global.showDeleted && idx.get(i).isActive()) {
					notebookIndex.add(new String(idx.get(i).getNotebookGuid()));
					noteIndex.add(new String(idx.get(i).getGuid()));
					activeIndex.add(new Boolean(idx.get(i).isActive()));					
				}
			}
		}
		threadLock.unlock();
	}
	public void release(int type) {
		readyQueue.add(type);
	}
	
	//*********************************************
	//* Getter & Setter method to tell the thread *
	//* to keep running.                          *
	//*********************************************
	public void setKeepRunning(boolean b) {
		keepRunning = b;
	}
	public boolean keepRunning() {
		return keepRunning;
	}
	
	
	//*********************************************
	//* Do the actual counting                    *
	//*********************************************
	private void countNotebookResults() {
		logger.log(logger.EXTREME, "Entering ListManager.countTagResults");		
		DatabaseConnection conn = new DatabaseConnection(logger, Global.tagCounterThreadId);
		List<NotebookCounter> nCounter = new ArrayList<NotebookCounter>();
		List<Notebook> books = conn.getNotebookTable().getAll();
				
		if (type == NOTEBOOK_ALL) {
			for (int i=0; i<books.size(); i++) {
				nCounter.add(new NotebookCounter());
				nCounter.get(i).setCount(0);
				nCounter.get(i).setGuid(books.get(i).getGuid());
			}
			List<Pair<String, Integer>> notebookCounts = conn.getNotebookTable().getNotebookCounts();
			for (int i=0; notebookCounts != null && i<notebookCounts.size(); i++) {
				for (int j=0; j<nCounter.size(); j++) {
					if (notebookCounts.get(i).getFirst().equals(nCounter.get(j).getGuid())) {
						nCounter.get(j).setCount(notebookCounts.get(i).getSecond());
						j=nCounter.size();
					}
				}
			}
			notebookSignal.countsChanged.emit(nCounter);
			return;
		}
		
		for (int i=notebookIndex.size()-1; i>=0 && keepRunning; i--) {
			boolean notebookFound = false;
			for (int j=0; j<nCounter.size() && keepRunning; j++) {
				if (nCounter.get(j).getGuid().equals(notebookIndex.get(i))) {
					notebookFound = true;
					if (activeIndex.get(i)) {
						int c = nCounter.get(j).getCount()+1;
						nCounter.get(j).setCount(c);
					}
					j=nCounter.size();
				}
			}
			if (!notebookFound) {
				NotebookCounter newCounter = new NotebookCounter();
				newCounter.setGuid(notebookIndex.get(i));
				newCounter.setCount(1);
				nCounter.add(newCounter);
			}
		}
		notebookSignal.countsChanged.emit(nCounter);
		logger.log(logger.EXTREME, "Leaving ListManager.countNotebookResults()");
	}
	
	
	private void countTagResults() {
		logger.log(logger.EXTREME, "Entering ListManager.countTagResults");		
		DatabaseConnection conn = new DatabaseConnection(logger, Global.tagCounterThreadId);
		List<TagCounter> counter = new ArrayList<TagCounter>();
		List<Tag> allTags = conn.getTagTable().getAll();
		
		if (allTags == null)
			return;
		for (int k=0; k<allTags.size() && keepRunning; k++) {
			TagCounter newCounter = new TagCounter();
			newCounter.setGuid(allTags.get(k).getGuid());
			newCounter.setCount(0);
			counter.add(newCounter);
		}
		
		if (type == TAG_ALL) {
			List<Pair<String, Integer>> tagCounts = conn.getNoteTable().noteTagsTable.getTagCounts();
			for (int i=0; tagCounts != null &&  i<tagCounts.size(); i++) {
				for (int j=0; j<counter.size(); j++) {
					if (tagCounts.get(i).getFirst().equals(counter.get(j).getGuid())) {
						counter.get(j).setCount(tagCounts.get(i).getSecond());
						j=counter.size();
					}
				}
			}
			tagSignal.countsChanged.emit(counter);
			return;
		}
		
		
		List<NoteTagsRecord> tags = conn.getNoteTable().noteTagsTable.getAllNoteTags();
		for (int i=noteIndex.size()-1; i>=0; i--) {
			String note = noteIndex.get(i);
			for (int x=0; x<tags.size() && keepRunning; x++) {
				String tag = tags.get(x).tagGuid;
				for (int j=0; j<counter.size() && keepRunning; j++) {
					if (counter.get(j).getGuid().equals(tag) && note.equals(tags.get(x).noteGuid)) {
						int c = counter.get(j).getCount()+1;
						counter.get(j).setCount(c);
					}
				}
			}
		}
		tagSignal.countsChanged.emit(counter);
		logger.log(logger.EXTREME, "Leaving ListManager.countTagResults()");
	}
	
	
	private void countTrashResults() {
		logger.log(logger.EXTREME, "Entering CounterRunner.countTrashResults()");		
		DatabaseConnection conn = new DatabaseConnection(logger, Global.trashCounterThreadId);
		Integer tCounter = conn.getNoteTable().getDeletedCount();
		trashSignal.countChanged.emit(tCounter);
		logger.log(logger.EXTREME, "Leaving CounterRunner.countTrashResults()");
	}

}
