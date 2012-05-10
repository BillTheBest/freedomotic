//Copyright 2009 Enrico Nicoletti
//eMail: enrico.nicoletti84@gmail.com
//
//This file is part of EventEngine.
//
//EventEngine is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//any later version.
//
//EventEngine is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with EventEngine; if not, write to the Free Software
//Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
package it.freedomotic.reactions;

import it.freedomotic.persistence.CommandPersistence;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author enrico
 */

public class CommandSequence implements Serializable {
    protected ArrayList<Command> commands = new ArrayList<Command>();

    public Iterator iterator() {
        return commands.iterator();
    }

    public int size() {
        return commands.size();
    }

    public void append(Command c) {
        if (c != null) {
            commands.add(c);
        }
    }

    public void append(String c) {
        Command pojo = CommandPersistence.getCommand(c);
        if (pojo != null) {
            commands.add(pojo);
        }
    }

    public ArrayList<Command> getCommands(){
        return commands;
    }
}
