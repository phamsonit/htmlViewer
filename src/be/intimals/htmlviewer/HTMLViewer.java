package be.intimals.htmlviewer;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static be.intimals.htmlviewer.Utils.*;
import static be.intimals.htmlviewer.Variables.*;

public class HTMLViewer {

    private String htmlDir;
    private String inputSourceDir;
    private String inputResultDir;

    //store line and column numbers of a match
    private Map<Integer, Set<String> > matchedLines;
    //store node IDs of a match in XML file
    private Set<Integer> nodeIdOfMatch;
    //python source code
    private List<String> pythonSource;

    public HTMLViewer(File _inputSourceDir, File _inputResultDir, File _htmlDir){
        this.inputSourceDir = _inputSourceDir.getAbsolutePath();
        this.inputResultDir = _inputResultDir.getAbsolutePath();
        this.htmlDir = _htmlDir.getAbsolutePath();
    }

    /**
     * show patterns in web browser
     */
    public void view(){
        //get configuration file
        String finalConfig = getConfigFileName(new File(inputResultDir));
        try {
            //read config file to find these information
            Config config = new Config(finalConfig);
            if(config.get2Class()){
                createHTMLForTwoClassPatterns(config);
            }else{
                createHTMLForOneClassPatterns(config);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        //view result in browser
        if(Files.exists(Paths.get(htmlDir+"/patterns.html"))) {
            try {
                initHTMLFiles(htmlDir);
                File htmlFile = new File(htmlDir + "/index.html");
                Desktop.getDesktop().browse(htmlFile.toURI());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * create html for patterns from a class
     * @param config
     */
    private void createHTMLForOneClassPatterns(Config config) {
        //get all matches
        String matchesFileName = inputResultDir+"/"+ getLastName(config.getOutputMatches());
        NodeList allMatches = readAllTagName(matchesFileName, "match");

        //get all patterns
        String patternFileName = inputResultDir+"/"+ getLastName(config.getOutputPath());
        NodeList allPatterns = readAllTagName(patternFileName,"subtree");
        int nbPattern = allPatterns.getLength();//count number of patterns

        //for each pattern find its matches from allMatches
        String patternContent = HTMLHEADER;
        patternContent += "<p><h3>List patterns: " + getLastName(inputResultDir) +"</h3></p>\n";
        for(int i = 1; i<=nbPattern; ++i){
            String aPattern = "<p><a href= \"pattern_"+i+"_matches_old.html\" target=\"center\">["+i+"]- pattern "+i+"</a></p>";
            patternContent += aPattern+"\n";
            findMatchesOfPattern(i, allMatches, inputSourceDir, "old");
        }
        patternContent += HTMLCLOSE+"\n";
        //create a patterns.html to list all patterns found
        writeHTML(htmlDir+"/patterns.html", patternContent);
    }

    /**
     * create output html file for patterns from two classes
     * @param config
     */
    private void createHTMLForTwoClassPatterns(Config config) {

        String matchesFile1 = inputResultDir+"/"+ getLastName(config.getOutputMatches1());
        String matchesFile2 = inputResultDir+"/"+ getLastName(config.getOutputMatches2());

        String oldInputSourceDir = inputSourceDir+"/"+getLastName(config.getInputFiles1());//"pos";
        String newInputSourceDir = inputSourceDir+"/"+getLastName(config.getInputFiles2());//"nag";

        //read all matches from matches files
        NodeList allMatchesOld = readAllTagName(matchesFile1,"match");
        NodeList allMatchesNew = readAllTagName(matchesFile2, "match");

        //for each pattern find matches in the old and new
        String patternFile = inputResultDir+"/"+getLastName(config.getOutputPath());//"q1_input_5_patterns.xml";
        NodeList allPatterns = readAllTagName(patternFile,"subtree");

        int nbPattern = allPatterns.getLength(); //count number of patterns
        //for each pattern find its matches from allMatches
        String patternContent = HTMLHEADER;
        patternContent += "<p><h3>List patterns: " + getLastName(inputResultDir) +"</h3></p>\n";
        for(int i = 1; i<=nbPattern; ++i){
            //get support
            String[] support = findSupport(i, allPatterns).split("-");
            int oldSup = Integer.valueOf(support[0]);
            int newSup = Integer.valueOf(support[1]);

            //create a link for this pattern in the pattern file
            String aPattern =
                    "<p>pattern-"+i+":\n" +
                            "   (\n" +
                            "   <a href= \"pattern_"+i+"_matches_old.html\" target=\"center\">"+oldSup+" matches old</a>\n" +
                            "   /\n" +
                            "   <a href= \"pattern_"+i+"_matches_new.html\" target=\"center\">"+newSup+" matches new</a>\n" +
                            "   )\n" +
                            "</p>\n";

            patternContent += aPattern;

            //create equivalent matches for this pattern
            findMatchesOfPattern(i, allMatchesOld, oldInputSourceDir, "old");
            findMatchesOfPattern(i, allMatchesNew, newInputSourceDir, "new");
        }
        patternContent += HTMLCLOSE+"\n";
        //create a patterns.html to list all patterns found
        writeHTML(htmlDir+"/patterns.html", patternContent);
    }

    /**
     * for each match of the given patternID create a html file
     * @param patternID
     * @param allMatches
     * @param inputDir
     */
    private void findMatchesOfPattern(int patternID, NodeList allMatches, String inputDir, String label){
        try {
            //for each match create a file named patternID_i_matchID_j
            String matchesContent = HTMLHEADER;
            matchesContent += "<p><h3>Matches of pattern-"+patternID+"</h3></p>\n";
            int count = 1; //count number of matches
            for (int i = 0; i < allMatches.getLength(); ++i) {
                String matchID = allMatches.item(i).getAttributes().getNamedItem("PatternID").getNodeValue();
                if (patternID == Integer.valueOf(matchID)) {
                    Node match = allMatches.item(i);
                    //get Python file name from a match
                    String pyFileName = inputDir+"/"+getFileNameFromMatch(match);
                    //TODO: consider java case
                    String xmlFile = pyFileName.substring(0, pyFileName.length() - 2) + "xml";
                    //find lines of a match in xmlFile
                    findLineVariableNames(match, xmlFile, pyFileName);
                    //add markers to lines corresponding lines in python file
                    String newContent = HTMLHEADER+
                            "Source code:"+ pyFileName +
                            "<code>\n" +
                            addMarkers()+
                            "</code>\n" +
                            HTMLCLOSE;
                    //write patternID_match_ID content to html file
                    String htmlFileName = "patternID_"+patternID+"_matchID_"+String.valueOf(count)+"_"+label+".html";
                    writeHTML(htmlDir+"/"+htmlFileName, newContent);

                    //add a link of match i to its matchesContent
                    String fullName = allMatches.item(i).getAttributes().getNamedItem("FullName").getNodeValue();
                    matchesContent += "<p><a href=\""+htmlFileName+"\" target=\"right\">["+count+"]-"+fullName+"</a></p>\n";
                    ++count;
                }
            }
            //write pattern_ID_matches content to file
            String tt = htmlDir+"/pattern_"+patternID+"_matches_"+label+".html";
            matchesContent += HTMLCLOSE;
            writeHTML(tt, matchesContent);

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * find lines and variable names of a match to add color markers
     * @param match     : one match
     * @param XMLFile   : XML file
     */
    private void findLineVariableNames(Node match, String XMLFile, String PythonFile){
        //store lines and variable names
        matchedLines = new HashMap<>();
        //store match node ids of a match
        nodeIdOfMatch = getNodeIdOfMatch(match);
        //read python file
        pythonSource = readPyFile(PythonFile);
        //read XML document
        Document doc = readXML(XMLFile);
        //get all nodes of the match
        NodeList nodes = match.getChildNodes();
        //find line number of node IDs in XML file
        for(int i=0; i<nodes.getLength(); ++i){
            if(nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                String id = nodes.item(i).getAttributes().getNamedItem("ID").getNodeValue();
                //find line and col number of this id in the XML doc
                findLineAndCol(id, doc.getDocumentElement());
            }
        }
    }

    /**
     * find all IDs of AST nodes in a match
     * @param match : a match
     * @return
     */
    private Set<Integer> getNodeIdOfMatch(Node match){
        Set<Integer> temp = new HashSet<>();
        NodeList nodes = match.getChildNodes();
        for(int i=0; i<nodes.getLength(); ++i){
            if(nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                String id = nodes.item(i).getAttributes().getNamedItem("ID").getNodeValue();
                temp.add(Integer.valueOf(id));
            }
        }
        return temp;
    }


    /**
     * find all line and column number of an match id in a XML file
     * @param matchID    : match ID of an XML node
     * @param node      : root node of XML file
     */
    private void findLineAndCol(String matchID, Node node){
        try {
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                if(node.getAttributes().getNamedItem("ID") != null){
                    //get ID value in XML file
                    String nodeID = node.getAttributes().getNamedItem("ID").getNodeValue();
                    if (nodeID.equals(matchID)) {//found nodeID in XML doc
                        String nodeLineNr = node.getAttributes().getNamedItem("LineNr").getNodeValue();
                        String nodeEndLineNr = node.getAttributes().getNamedItem("EndLineNr").getNodeValue();
                        String nodeColNr = node.getAttributes().getNamedItem("ColNr").getNodeValue();
                        String nodeEndColNr = node.getAttributes().getNamedItem("EndColNr").getNodeValue();

                        int variableLength = Integer.valueOf(nodeEndColNr) - Integer.valueOf(nodeColNr);
                        boolean isMultipleLines =  Integer.valueOf(nodeEndLineNr) > Integer.valueOf(nodeLineNr);
                        String variableName="";
                        //get variable name
                        if( isMultipleLines && node.getChildNodes().getLength() == 1 ) {
                            variableName = "comment";
                        }else {
                            if (node.getChildNodes().getLength() == 1) {
                                variableName = nodeColNr + numSep + nodeEndColNr + strSep + node.getTextContent().trim();
                            } else {
                                //find dummy variable line and column number
                                //intermediate node, keywords like ClassDef, FunctionDef, If,
                                if (isDummyMatch(matchID, node) && !getDummyVariable(node).isEmpty()) {
                                    //System.out.println(node.getNodeName()+" "+getDummyVariable(node));
                                    variableName = nodeColNr + numSep + nodeEndColNr + strSep + getDummyVariable(node) + strSep + "dummy";
                                }
                            }
                        }

                        //this node doesn't have variable name or dummy name, e.g, Module, Func, ...
                        if(variableName.isEmpty()) return;

                        if( isMultipleLines && node.getChildNodes().getLength() > 1) {
                            // dummy variable is in multiple lines, e.g, comments
                            if(isDummyMatch(matchID, node) && !getDummyVariable(node).isEmpty()){
                                addMultipleLineID(nodeLineNr, nodeEndLineNr);
                            }
                        }else{
                            // matched variable has multiple lines, e.g, comments
                            if( isMultipleLines && node.getChildNodes().getLength() == 1){
                                addMultipleLineID(nodeLineNr, nodeEndLineNr);
                            }else {
                                int lineLength = pythonSource.get(Integer.valueOf(nodeLineNr)-1).length()-1;
                                if(variableLength == lineLength) {
                                    // add matched comment in single line
                                    addLineAndVariables("", Integer.valueOf(nodeLineNr));
                                }
                                else {
                                    // add matched variable in single line
                                    addLineAndVariables(variableName, Integer.valueOf(nodeLineNr));
                                }
                            }
                        }
                    } else {//search matchID in children list
                        if (node.hasChildNodes()) {
                            NodeList nodeList = node.getChildNodes();
                            for (int i = 0; i < nodeList.getLength(); ++i) {
                                findLineAndCol(matchID, nodeList.item(i));
                            }
                        }
                    }
                }else{//search matchID from other nodes
                    if (node.hasChildNodes()) {
                        NodeList nodeList = node.getChildNodes();
                        for (int i = 0; i < nodeList.getLength(); ++i) {
                            findLineAndCol(matchID, nodeList.item(i));
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void addMultipleLineID(String nodeLineNr, String nodeEndLineNr) {
        for (int i = Integer.valueOf(nodeLineNr); i <= Integer.valueOf(nodeEndLineNr); ++i) {
            //add an empty string to this line id
            addLineAndVariables("", i);
        }
    }

    /**
     * return true if the matchID is a dummy match
     * @param matchID : match ID
     * @param node : XML doc
     * @return
     */
    private boolean isDummyMatch(String matchID, Node node){
        try {
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                if(node.getAttributes().getNamedItem("ID") != null){
                    //get ID value in XML file
                    String nodeID = node.getAttributes().getNamedItem("ID").getNodeValue();
                    if (nodeID.equals(matchID)) {//if found nodeID in XML doc
                        NodeList nodeList = node.getChildNodes();
                        for(int i=0; i<nodeList.getLength(); ++i){
                            if(nodeList.item(i).getNodeType() == Node.ELEMENT_NODE)
                                //if the ID of this child node is not in the nodeIdOfMatch then this node is a dummy node
                                if(!nodeIdOfMatch.contains(Integer.valueOf(nodeList.item(i).getAttributes().getNamedItem("ID").getNodeValue())))
                                    return true;
                        }
                    } else {//search matchID in children list
                        if (node.hasChildNodes()) {
                            NodeList nodeList = node.getChildNodes();
                            for (int i = 0; i < nodeList.getLength(); ++i) {
                                isDummyMatch(matchID, nodeList.item(i));
                            }
                        }
                    }
                }else{//Search matchID from other nodes
                    if (node.hasChildNodes()) {
                        NodeList nodeList = node.getChildNodes();
                        for (int i = 0; i < nodeList.getLength(); ++i) {
                            isDummyMatch(matchID, nodeList.item(i));
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * find variable of a dummy node
     * @param node
     * @return
     */
    private String getDummyVariable(Node node){
        String dummy="";
        if(node.hasChildNodes()){
            NodeList nodeList = node.getChildNodes();
            for(int i=0; i<nodeList.getLength(); ++i){
                if(nodeList.item(i).getNodeType() == Node.ELEMENT_NODE){
                    if(nodeList.item(i).hasChildNodes()){
                        NodeList children = nodeList.item(i).getChildNodes();
                        for(int j=0; j< children.getLength(); ++j){
                            if(!children.item(j).getTextContent().equals("#text")){
                                dummy = children.item(j).getTextContent().trim();
                            }
                        }
                    }
                }
            }
        }
        return dummy;
    }


    /**
     * add variable Name and it colNr to line i
     * @param variableName : variable name
     * @param lineID : line i^th
     */
    private void addLineAndVariables(String variableName, int lineID) {
        if(! matchedLines.containsKey(lineID)){
            Set<String> val = new HashSet<>();
            val.add(variableName);
            matchedLines.put(lineID, val);
        }else{
            Set<String> val = matchedLines.get(lineID);
            val.add(variableName);
            matchedLines.put(lineID, val);
        }
    }


    /**
     * add makers to Python file
     * @return
     */
    private String addMarkers(){
        StringBuilder sb = new StringBuilder();
        try {
            for(int i=0; i<pythonSource.size(); ++i){
                String line = pythonSource.get(i);
                if(matchedLines.containsKey(i+1)){
                    //System.out.println("old "+line);
                    String newLine;
                    // if this line contains an empty string this line is comment
                    if(matchedLines.get(i+1).contains("")){
                        newLine = "<pre><p><mark><"+COMMENT+">" + line + "</"+COMMENT+"></mark><p></pre>" + "\n";
                    }else{
                        //add color marker to variable names
                        line = addMarkerToVariable(line, matchedLines.get(i+1));
                        //add color marker to keywords
                        line = addMarkerToKeyword(line, keywords);
                        //add marker to highlight entire line
                        newLine = "<pre><p><mark>" + line + "</mark><p></pre>" + "\n";
                    }
                    sb.append(newLine);
                    //System.out.println("new "+newLine);
                }else{
                    String newLine = "<pre>" + line + "</pre>\n";
                    sb.append(newLine);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return sb.toString();
    }


    /**
     * add color marker to variable names
     * @param line : line of code
     * @param variables : list of variable names
     * @return
     */
    private String addMarkerToVariable(String line, Set<String> variables){
        try {
            while (!variables.isEmpty()) {
                Iterator<String> variable = variables.iterator();
                if (variable.hasNext()) {
                    String element = variable.next();
                    String[] temp = element.split(strSep); // dummy/variable = ColNr#EndColNr;variableName;dummy / ColNr#EndColNr;variableName

                    String markedIdentifier;
                    if (temp.length == 2) {
                        markedIdentifier = "<" + VARCOLOR + ">" + temp[1] + "</" + VARCOLOR + ">";
                    } else {
                        markedIdentifier = "<" + DUMCOLOR + ">" + temp[1] + "</" + DUMCOLOR + ">";
                    }
                    //find the current colNr
                    int from = Integer.valueOf(temp[0].split(numSep)[0]);
                    int to = Integer.valueOf(temp[0].split(numSep)[1]);
                    //replace marked identifier
                    line = line.substring(0, from - 1) +
                            markedIdentifier +
                            line.substring(to);
                    //remover current variable
                    variable.remove();
                    //update column number for other variables
                    if (!variables.isEmpty()) {
                        variables = updateVariableColNr(variables, from);
                    }
                }
            }
        }catch (Exception e){
            System.out.println("add color marker error");
            System.out.println(line+" "+variables);
        }
        return line;
    }

    /**
     * update ColNr of variables after inserting color markers
     * @param variables
     * @param from
     * @return
     */
    private Set<String> updateVariableColNr(Set<String> variables, int from) {
        Set<String> newVariables = new HashSet<>();
        for (Iterator<String> variableLoop = variables.iterator(); variableLoop.hasNext(); ) {
            String elementLoop = variableLoop.next();
            String[] tempLoop = elementLoop.split(strSep);
            int fromLoop = Integer.valueOf(tempLoop[0].split(numSep)[0]);
            int toLoop = Integer.valueOf(tempLoop[0].split(numSep)[1]);
            if (fromLoop > from) {
                fromLoop += nbAddedCharacters; //number of added characters
                toLoop += nbAddedCharacters; //number of added characters
            }
            //update colNr
            String newVariable;
            if (tempLoop.length == 2) {
                newVariable = String.valueOf(fromLoop) + numSep + toLoop + strSep + tempLoop[1];
            } else {
                newVariable = String.valueOf(fromLoop) + numSep + toLoop + strSep + tempLoop[1] + strSep + tempLoop[2];
            }
            newVariables.add(newVariable);
        }
        return newVariables;
    }

    /**
     * add color marker to keywords
     * @param line : line of code
     * @return
     */
    private String addMarkerToKeyword(String line, Set<String> keywords){
        String[]temp = line.split(" ");
        for(int i=0; i<temp.length; ++i){
            if(!temp[i].isEmpty()) {
                if (keywords.contains(temp[i].trim())) {
                    String markedIdentifier = "<"+KEYCOLOR+">" + temp[i] + "</"+KEYCOLOR+">";
                    int index = findIdentifierIndex(line, temp[i]);
                    line = line.substring(0, index-1)+
                            markedIdentifier+
                            line.substring(index + temp[i].length()-1);
                }
            }
        }
        return line;
    }

     /**
     * return all nodes that have tagName name
     * @param fileName : input xml file
     * @param tagName  : tag name
     * @return
     */
    private NodeList readAllTagName(String fileName, String tagName){
        //read XML file
        NodeList matches = null;
        try {
            Document doc = readXML(fileName);
            matches = doc.getElementsByTagName(tagName);
        }catch (Exception e){
            e.printStackTrace();
        }
        return matches;
    }

}
