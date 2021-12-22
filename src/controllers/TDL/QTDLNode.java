package controllers.TDL;

import dungeon.play.PlayMap;

import java.util.Vector;

public class QTDLNode {

    public PlayMap playMap;
    public QTDLNode parentState;
    public int parentMove;
    public Vector<QTDLNode> childrenNodes;
    public float reward;

    public QTDLNode(PlayMap map) {
        this.playMap = map;
    }
}
