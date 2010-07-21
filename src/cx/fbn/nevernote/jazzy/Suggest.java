package cx.fbn.nevernote.jazzy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.swabunga.spell.engine.SpellDictionary;
import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.event.SpellCheckEvent;
import com.swabunga.spell.event.SpellCheckListener;
import com.swabunga.spell.event.SpellChecker;
import com.swabunga.spell.event.StringWordTokenizer;

import cx.fbn.nevernote.Global;
import cx.fbn.nevernote.utilities.Pair;

public class Suggest {

  ArrayList<Pair<String, ArrayList<String>>> words;
  
  public Suggest() {
	  words = new ArrayList<Pair<String, ArrayList<String>>>();
  }
  
  public class SuggestionListener implements SpellCheckListener {
	  
    @SuppressWarnings("unchecked")
	public void spellingError(SpellCheckEvent event) {
    	Pair<String,ArrayList<String>> newEntry = new Pair<String,ArrayList<String>>();
    	ArrayList newEntryWords = new ArrayList<String>();
    	newEntry.setFirst(event.getInvalidWord());
//    	System.out.println("Misspelling: " + event.getInvalidWord());

      List suggestions = event.getSuggestions();
      if (!suggestions.isEmpty()) {
        for (Iterator i = suggestions.iterator(); i.hasNext();) {
        	newEntryWords.add(i.next());
        }
      }
      newEntry.setSecond(newEntryWords);
      words.add(newEntry);
    }

  }

  public void check(String text) {

    SpellDictionary dictionary;
	try {
		dictionary = new SpellDictionaryHashMap(new File(Global.currentDir+"/english.0"));
	
		SpellChecker spellChecker = new SpellChecker(dictionary);
		spellChecker.addSpellCheckListener(new SuggestionListener());
		spellChecker.checkSpelling(new StringWordTokenizer(text));
	} catch (FileNotFoundException e) {
		e.printStackTrace();
	} catch (IOException e) {
		e.printStackTrace();
	}
  }

}