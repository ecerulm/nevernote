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

import java.util.HashMap;

public class StringUtils {

	  private StringUtils() {}
	  
	  private static HashMap<String,String> htmlEntities;
	  static {
	    htmlEntities = new HashMap<String,String>();
	    htmlEntities.put("&lt;","<")    ; htmlEntities.put("&gt;",">");
	    htmlEntities.put("&amp;","&")   ; htmlEntities.put("&quot;","\"");
	    htmlEntities.put("&agrave;","�"); htmlEntities.put("&agrave;","�");
	    htmlEntities.put("&acirc;","�") ; htmlEntities.put("&auml;","�");
	    htmlEntities.put("&auml;","�")  ; htmlEntities.put("&acirc;","�");
	    htmlEntities.put("&aring;","�") ; htmlEntities.put("&aring;","�");
	    htmlEntities.put("&aelig;","�") ; htmlEntities.put("&aElig;","�" );
	    htmlEntities.put("&ccedil;","�"); htmlEntities.put("&ccedil;","�");
	    htmlEntities.put("&eacute;","�"); htmlEntities.put("&eacute;","�" );
	    htmlEntities.put("&egrave;","�"); htmlEntities.put("&egrave;","�");
	    htmlEntities.put("&ecirc;","�") ; htmlEntities.put("&ecirc;","�");
	    htmlEntities.put("&euml;","�")  ; htmlEntities.put("&euml;","�");
	    htmlEntities.put("&iuml;","�")  ; htmlEntities.put("&iuml;","�");
	    htmlEntities.put("&ocirc;","�") ; htmlEntities.put("&ocirc;","�");
	    htmlEntities.put("&ouml;","�")  ; htmlEntities.put("&ouml;","�");
	    htmlEntities.put("&oslash;","�") ; htmlEntities.put("&oslash;","�");
	    htmlEntities.put("&szlig;","�") ; htmlEntities.put("&ugrave;","�");
	    htmlEntities.put("&ugrave;","�"); htmlEntities.put("&ucirc;","�");
	    htmlEntities.put("&ucirc;","�") ; htmlEntities.put("&uuml;","�");
	    htmlEntities.put("&uuml;","�")  ; htmlEntities.put("&nbsp;"," ");
	    htmlEntities.put("&copy;","\u00a9"); htmlEntities.put("&apos;", "'");
	    htmlEntities.put("&reg;","\u00ae"); htmlEntities.put("&iexcl;", "\u00a1");
	    htmlEntities.put("&euro;","\u20a0"); htmlEntities.put("&cent;", "\u00a2");
	    htmlEntities.put("&pound;", "\u00a3"); htmlEntities.put("&curen;", "\u00a4");
	    htmlEntities.put("&yen;", "\u00a5"); htmlEntities.put("&brvbar;", "\u00a6");
	    htmlEntities.put("&sect;", "\u00a7"); htmlEntities.put("&uml;", "\u00a8");
	    htmlEntities.put("&copy;", "\u00a9"); htmlEntities.put("&ordf;", "\u00aa");
	    htmlEntities.put("&laqo;", "\u00ab"); htmlEntities.put("&not;", "\u00ac");
	    htmlEntities.put("&reg;", "\u00ae"); htmlEntities.put("&macr;", "\u00af");
	  }


	  
	  public static final String unescapeHTML(String source, int start){
		     int i,j;

		     i = source.indexOf("&", start);
		     while (i>-1) {
		        j = source.indexOf(";" ,i);
		        if (j > i) {
		           String entityToLookFor = source.substring(i , j + 1);
		           String value = htmlEntities.get(entityToLookFor);
		           if (value != null) {
		        	   value = " ";
		        	   source = new StringBuffer().append(source.substring(0 , i).toLowerCase())
	                                   .append(value)
	                                   .append(source.substring(j + 1))
	                                   .toString();
		        	   i = source.indexOf("&", i+1);
		           }
		        }
		     }
		     return source;
		  }

	  
	  public static final String unescapeHTML2(String source, int start){
	     int i,j;

	     i = source.indexOf("&", start);
	     if (i > -1) {
	        j = source.indexOf(";" ,i);
	        if (j > i) {
	           String entityToLookFor = source.substring(i , j + 1);
	           String value = htmlEntities.get(entityToLookFor);
	           if (value != null) {
	        	   value = " ";
	        	   source = new StringBuffer().append(source.substring(0 , i).toLowerCase())
                                   .append(value)
                                   .append(source.substring(j + 1))
                                   .toString();
                return unescapeHTML(source, i + 1); // recursive call
	           }
	        }
	     }
	     return source;
	  }
}
