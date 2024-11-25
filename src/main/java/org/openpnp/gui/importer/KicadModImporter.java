/*
 * Copyright (C) 2023 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui.importer;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.model.Footprint;
import org.openpnp.model.Footprint.Pad;
import org.pmw.tinylog.Logger;

/**
 * @author Jonas Lehmke <jonas@lehmke.xyz>
 * 
 * This module reads a KiCad footprint from file (*.kicad_mod) and parses it to a Footprint instance.
 * Rectangular, rounded rectangular, circular and oval pad shapes are supported. Trapezoid and custom 
 * pad shapes are ignored. A FileDialog is openened to select a file once this class is instantiated. 
 * Imported pads are available as List<Pad>.
 * 
 * This module may become part of an all-in-one KiCad board import one day.
 */

 
public class KicadModImporter {

    Footprint footprint = new Footprint();

    public class SexpressionNode {
        String name = null;
        ArrayList<String> items = new ArrayList<String>();
        LinkedList<SexpressionNode> children = new LinkedList<SexpressionNode>();

        SexpressionNode() {
        }

        void setName(String nodeName) {
            name = nodeName;
        }

        void addItem(String x) {
            items.add(x);
        }

        void addChild(SexpressionNode child) {
            children.add(child);
        }

        String getItem(int index) {
            if (index < items.size()) {
                return items.get(index);
            }
            else {
                return null;
            }
        }

        int readChar(PushbackInputStream stream) throws IOException {
            int c = stream.read();

            if (c == -1) {
                Logger.trace("read EOF, when expecting a character");
                throw new IOException("Unexpected EOF reading footprint file");
            }
            // Logger.trace("read:" + Character.toString(c));
            return c;
        }
        
        void skipSpace(PushbackInputStream stream) throws IOException {
            while(true) {
                int c = readChar(stream);
                if (!Character.isWhitespace(c)) {
                    stream.unread(c);
                    return;
                }
            }
        }
        
        String readString(PushbackInputStream stream) throws IOException {
            StringBuilder result = new StringBuilder();

            // we are just not going to do escaped double quotes
            while(true) {
                int c = readChar(stream);
                if (c == '"') {
                    return result.toString();
                }
                result.append((char)c);
            }
        }
        
        String readToken(PushbackInputStream stream) throws IOException {
            int c;

            while(true) {
                skipSpace(stream);

                c = readChar(stream);

                if (c == '"') {
                    Logger.trace("reading string...");
                    return readString(stream);
                }

                if (c == '(') {
                    Logger.trace("found nested sexpr...");
                    stream.unread(c);
                    SexpressionNode child = new SexpressionNode();
                    if (child.parse(stream)) {
                        addChild(child);
                    }
                    continue;
                }

                if (c != ')') {
                    Logger.trace("found start of token");
                    break;
                }

                Logger.trace("found end of nested sexpr");
                return null; // got end paren, end of expression
            }

            StringBuilder result = new StringBuilder();

            result.append((char)c);

            while(true) {
                c = readChar(stream);
                if (Character.isWhitespace(c)) {
                    return result.toString();
                }
                if (c == ')' || c == '(') {
                    stream.unread(c);
                    return result.toString();
                }
                result.append((char)c);
            }
        }

        boolean parse(PushbackInputStream stream) throws IOException {
            skipSpace(stream);

            int c = readChar(stream);

            if (c !='(') {
                System.out.println("Warning: unexpected character in stream=" + c);
                return false;
            }


            name = readToken(stream);

            if (name == null) {
                return false;
            }

            Logger.trace("found sexpr with name " + name);

            while(true) {
                String s = readToken(stream);

                Logger.trace("read item token " + s);

                if (s == null) {
                    return true;
                }

                addItem(s);
            }
        }

        SexpressionNode findNode(String nameToFind) {
            LinkedList<SexpressionNode> nodes = findNodesNamed(nameToFind);
            if (nodes == null) {
                return null;
            }

            return nodes.getFirst();
        }

        LinkedList<SexpressionNode> findNodesNamed(String nameToFind) {
            LinkedList<SexpressionNode> result = null;

            if (name.equals(nameToFind)) {
                result = new LinkedList<SexpressionNode>();
                result.add(this);
            }
            else {
                for(SexpressionNode node : children) {
                    LinkedList<SexpressionNode> child_result = node.findNodesNamed(nameToFind);

                    if (child_result != null) {
                        if (result == null) {
                            result = new LinkedList<SexpressionNode>();
                        }
                        result.addAll(child_result);
                    }
                }
            }
            return result;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            result.append('(');
            result.append(name);
            result.append(' ');

            result.append(String.join(" ", items));
            
            result.append(' ');

            for(SexpressionNode node : children) {
                result.append('\n');
                result.append(node.toString());
            }
            result.append(")\n");
            return result.toString();
        }
    }

    public class KicadPad {
    
        SexpressionNode sexpr;
        /*
         Each pad looks something like this:        
           (pad "1" smd roundrect
                   (at -1.4625 0)
                   (size 1.125 1.75)
                   (layers "F.Cu" "F.Paste" "F.Mask")
                   (roundrect_rratio 0.222222)
                   (uuid "dbad0287-b397-45bd-8a16-43e87bdf7c5c")
           )
        */

        public KicadPad(SexpressionNode definition) {
            sexpr = definition;
        }

        String getName() {
            return sexpr.getItem(0);
        }

        String getType() {
            return sexpr.getItem(1);
        }

        String getShape() {
            return sexpr.getItem(2);
        }

        double getDoubleElement(String name, int index) {
            SexpressionNode node = sexpr.findNode(name);
            if (node == null) {
                return 0.0;
            }

            String s = node.getItem(index);

            if (s == null) {
                return 0.0;
            }

            return Double.parseDouble(s);

        }

        double getSizeElement(int index) {
            return getDoubleElement("size", index);
        }

        double getWidth() {
            return getSizeElement(0);
        }

        double getHeight() {
            return getSizeElement(1);
        }

        double getX() {
            return getDoubleElement("at", 0);
        }

        double getY() {
            return getDoubleElement("at", 1);
        }

        double getRotation() {
            return getDoubleElement("at", 2);
        }

        double getRoundness() {
            return getDoubleElement("roundrect_rratio", 0);
        }

        boolean isTopCu() {
            SexpressionNode node = sexpr.findNode("layers");

            if (node == null) {
                return false;
            }

            for(String s : node.items) {
                if (s.equals("F.Cu") || s.contains("*.Cu")) {
                    return true;
                }
            }

            return false;
        }
    }

    public KicadModImporter() throws Exception {
        try {
            FileDialog fileDialog = new FileDialog(MainFrame.get());
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".kicad_mod"); //$NON-NLS-1$
                }
            });
            fileDialog.setVisible(true);
            if (fileDialog.getFile() == null) {
                return;
            }

            File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());
            PushbackInputStream stream = new PushbackInputStream(new FileInputStream(file));
            
            Logger.trace("KicadModImporter: opened module file " + fileDialog.getFile());

            SexpressionNode root = new SexpressionNode();

            try {
                if (root.parse(stream)) {
                    Logger.trace("KicadModImporter: read sexpression=" + root);
                }
                else {
                    Logger.trace("KicadModImporter: parse returned false, sexpression=" + root);
                }
            }
            catch (Exception e) {
                Logger.trace("KicadModImporter: parse exception = " + e + ", sexpression=" + root);
                e.printStackTrace();
                throw new Exception(Translations.getString("KicadModImporter.LoadFile.Fail") + e.getMessage()); //$NON-NLS-1$
            }
    
            /*
             * The root of the sexpression is "footprint"
             * It has children which have the "pad" sexpr
             * Each pad looks something like this:
             *        
                (pad "1" smd roundrect
                        (at -1.4625 0)
                        (size 1.125 1.75)
                        (layers "F.Cu" "F.Paste" "F.Mask")
                        (roundrect_rratio 0.222222)
                        (uuid "dbad0287-b397-45bd-8a16-43e87bdf7c5c")
                )
             */
            LinkedList<SexpressionNode> pads = root.findNodesNamed("pad");

            if (pads == null) {
                Logger.trace("KicadModImporter: no pads found in sexpression=" + root);
                throw new Exception("No pads found in Kicad Module");
            }

            for(SexpressionNode pad_node : pads) {
                Logger.trace("KicadModImporter: found pad "+ pad_node);
                KicadPad kipad = new KicadPad(pad_node);
                if (kipad.getType().equals("smd") && kipad.isTopCu()) {
                    Logger.trace("KicadModImporter: found smd pad " + kipad);
                    
                    Pad pad = new Pad();

                    pad.setName(kipad.getName());
                    pad.setWidth(kipad.getWidth());
                    pad.setHeight(kipad.getHeight());
                    pad.setX(kipad.getX());
                    pad.setY(kipad.getY());
                    pad.setRotation(kipad.getRotation());

                    if (kipad.getShape().equals("rect")) {
                        pad.setRoundness(0);
                    } else if (kipad.getShape().equals("circle")) {
                        pad.setRoundness(100);
                    } else if (kipad.getShape().equals("oval")) {
                        pad.setRoundness(100);
                    } else if (kipad.getShape().equals("roundrect")) {
                        pad.setRoundness(kipad.getRoundness());
                    } else {
                        System.out.println("Warning: Unsupported pad type: " + kipad.getShape());
                        continue;
                    }

                    footprint.addPad(pad);
                }
            }

            stream.close();
        }
        catch (Exception e) {
            throw new Exception(Translations.getString("KicadModImporter.LoadFile.Fail") + e.getMessage()); //$NON-NLS-1$
        }
    }

    public List<Pad> getPads() {
        return footprint.getPads();
    }
}
