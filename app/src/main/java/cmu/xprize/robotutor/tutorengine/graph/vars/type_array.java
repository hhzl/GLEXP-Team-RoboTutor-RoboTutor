//*********************************************************************************
//
//    Copyright(c) 2016 Carnegie Mellon University. All Rights Reserved.
//    Copyright(c) Kevin Willows All Rights Reserved
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
//*********************************************************************************

package cmu.xprize.robotutor.tutorengine.graph.vars;

import cmu.xprize.robotutor.tutorengine.graph.graph_node;


public class type_array extends graph_node {

    public IArraySource _owner;
    public String       _listName;


    public type_array(IArraySource owner, String listName ) {
        _owner    = owner;
        _listName = listName;
    }

    public String resolve(int index) {

        return _owner.deReference(_listName, index);
    }

    @Override
    public int getIntValue() {
        return 0;
    }
}
