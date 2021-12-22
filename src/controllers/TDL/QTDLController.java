package controllers.TDL;

import controllers.Controller;
import controllers.RandomController;
import dungeon.play.GameCharacter;
import dungeon.play.PlayMap;
import util.math2d.Point2D;

import java.util.ArrayList;
import java.util.Random;

public class QTDLController extends Controller {

    int prevAction = PlayMap.IDLE;
    int currAction = PlayMap.IDLE;

    public QTDLController(PlayMap playMap, GameCharacter controllingChar) {
        super(playMap, controllingChar);
    }

    public QTDLController(PlayMap playMap, GameCharacter controllingChar, String label) {
        super(playMap, controllingChar, label);
    }

    final int totalRuns = 50;
    final int maxActions = 300;
    float[][] valueEstimates;

    private final double alpha = 0.1; // (a) learning rate
    private final double gamma = 0.9; // (y) discount factor

    private final int mazeWidth = 12;
    private final int mazeHeight = 12;
    private final int statesCount = mazeHeight * mazeWidth;
    private final int actionsCount = 4;

    private char[][] maze;
    private int[][] rewardsTable;
    private double[][] Qtable;

    private int startState, finalState;
    private int exitReward = 10, treasureReward = 2, potionReward = 1, monsterPenalty = -2, emptySpace = 0;

    @Override
    public int getNextAction() {

        initRewardTable();
        fillRewardTable();
        printRewardsTable(rewardsTable);
        initQTable();
        calculateQ();
        printQ();
        printPolicy();
//        initNonTerminalStatesToZero();

        Controller randomAgent = new RandomController(map, map.getHero());
        return randomAgent.getNextAction();
    }

    /**
     * Initialize Q values to 0
     */
    private void initQTable() {
        Qtable = new double[statesCount][actionsCount];
        for (int i = 0; i < statesCount; i++) {
            for (int j = 0; j < actionsCount; j++) {
                Qtable[i][j] = 0;
            }
        }
    }

    /**
     * Initialize reward values with -1
     */
    private void initRewardTable() {
        rewardsTable = new int[statesCount][actionsCount];
        for (int i = 0; i < statesCount; i++) {
            for (int j = 0; j < actionsCount; j++) {
                rewardsTable[i][j] = -1;
            }
        }
    }

    /**
     * Maps consist of empty tiles (.), impassable wall tiles (#),
     * the entrance where the hero starts from (E),
     * the exit where the hero must go to complete the level (X),
     * treasure tiles (r), potions (p) and monsters (m).
     * A hero starts at the entrance with 40 hit points (HP);
     * stepping on a monster tile the first time kills the monster
     * but deals 6-15 HP damage to the hero; stepping on a potion
     * tile the first time removes the potion and heals the hero by 10 HP.
     * <p>
     * Moves
     * UP = 0;
     * RIGHT = 1;
     * DOWN = 2;
     * LEFT = 3;
     */
    private void fillRewardTable() {
        maze = new char[mazeHeight][mazeWidth];

        fillMaze();

        int i = 0, j = 0;
        for (int k = 0; k < statesCount; k++) {

            // We will navigate with i and j through the maze, so we need
            // to translate k into i and j
            i = k / mazeWidth;
            j = k - i * mazeWidth;
            if (maze[i][j] == '@')
                startState = k;
            if (maze[i][j] != 'X') {
                // Try to move up in the maze
                int up = i - 1;
                if (up >= 0) {
                    int target = up * mazeWidth + j;
                    switch (maze[up][j]) {
                        case 'X' -> rewardsTable[k][0] = exitReward;
                        case 'r' -> rewardsTable[k][0] = treasureReward;
                        case 'p' -> rewardsTable[k][0] = potionReward;
                        case 'm' -> rewardsTable[k][0] = monsterPenalty;
                        case '.' -> rewardsTable[k][0] = emptySpace;
                    }
                }
                // Try to move right in the maze
                int right = j + 1;
                if (right < mazeWidth) {
                    int target = i * mazeWidth + right;
                    switch (maze[i][right]) {
                        case 'X' -> rewardsTable[k][1] = exitReward;
                        case 'r' -> rewardsTable[k][1] = treasureReward;
                        case 'p' -> rewardsTable[k][1] = potionReward;
                        case 'm' -> rewardsTable[k][1] = monsterPenalty;
                        case '.' -> rewardsTable[k][1] = emptySpace;
                    }
                }
                // Try to move down in the maze
                int down = i + 1;
                if (down < mazeHeight) {
                    int target = down * mazeWidth + j;
                    switch (maze[down][j]) {
                        case 'X' -> rewardsTable[k][2] = exitReward;
                        case 'r' -> rewardsTable[k][2] = treasureReward;
                        case 'p' -> rewardsTable[k][2] = potionReward;
                        case 'm' -> rewardsTable[k][2] = monsterPenalty;
                        case '.' -> rewardsTable[k][2] = emptySpace;
                    }
                }
                // Try to move left in the maze
                int left = j - 1;
                if (left >= 0) {
                    int target = i * mazeWidth + left;
                    switch (maze[i][left]) {
                        case 'X' -> rewardsTable[k][3] = exitReward;
                        case 'r' -> rewardsTable[k][3] = treasureReward;
                        case 'p' -> rewardsTable[k][3] = potionReward;
                        case 'm' -> rewardsTable[k][3] = monsterPenalty;
                        case '.' -> rewardsTable[k][3] = emptySpace;
                    }
                }
            } else {
                finalState = k;
            }
        }
    }

    private void fillMaze() {
        String asciiMap = map.toASCII();
        char[] charMap = asciiMap.toCharArray();
        int i = 0, j = 0;
        for (char c : charMap) {
            if (c == '\n') {
                i++;
                j = 0;
                continue;
            }
            maze[i][j] = c;
            j++;
            if (i == 11) break;
        }
    }

    private void printMazeCreated() {
        System.out.println("This is the maze built by me:");
        for (int i = 0; i < mazeHeight; i++) {
            for (int j = 0; j < mazeWidth; j++) {
                System.out.print(maze[i][j]);
            }
            System.out.println();
        }
    }

    private void printRewardsTable(int[][] matrix) {
        System.out.printf("%25s", "States: ");
        for (int i = 0; i < actionsCount; i++) {
            System.out.printf("%4s", i);
        }
        System.out.println();

        for (int i = 0; i < statesCount; i++) {
            System.out.print("Possible states from " + i + " :[");
            for (int j = 0; j < actionsCount; j++) {
                System.out.printf("%4s", matrix[i][j]);
            }
            System.out.println("]");
        }
    }

    /**
     * Initialize the Q-values table, Q(s, a).
     * Observe the current state, s.
     * Choose an action, a, for that state
     * Take the action, and observe the reward, r, as well as the new state, s'.
     * Update the Q-value for the state using the observed reward and the maximum reward possible for the next state.
     * Set the state to the new state, and repeat the process until a terminal state is reached.
     */
    void calculateQ() {
        Random rand = new Random();

        for (int i = 0; i < totalRuns; i++) {
            // Select random initial state
            int crtState = startState;
            int steps = 0;
            while (!isFinalState(crtState) && steps < maxActions) {
                steps++;
                int[] actionsFromCurrentState = possibleActionsFromState(crtState);

                // Pick a random action from the ones possible

                int index = rand.nextInt(actionsFromCurrentState.length);
                int action = actionsFromCurrentState[index];

                // Q(state,action)= Q(state,action) + alpha * (R(state,action) + gamma * Max(next state, all actions) - Q(state,action))
                double q = Qtable[crtState][action];
                double maxQ = maxQ(crtState, action);
                int r = rewardsTable[crtState][action];

                double value = q + alpha * (r + gamma * maxQ - q);
                Qtable[crtState][action] = value;

                int nextState = crtState;
                switch (action) {
                    case 0 -> nextState -= 12;
                    case 1 -> nextState++;
                    case 2 -> nextState += 12;
                    case 3 -> nextState--;
                }
                crtState = nextState;
            }
        }
    }

    boolean isFinalState(int state) {
//        int i = state / mazeWidth;
//        int j = state - i * mazeWidth;
//
//        return maze[i][j] == 'X';
        return state==finalState;
    }

    int[] possibleActionsFromState(int state) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < actionsCount; i++) {
            if (rewardsTable[state][i] != -1) {
                result.add(i);
            }
        }

        return result.stream().mapToInt(i -> i).toArray();
    }

    double maxQ(int crtState, int action) {

        int nextState = crtState;
        switch (action) {
            case 0 -> nextState -= 12;
            case 1 -> nextState++;
            case 2 -> nextState += 12;
            case 3 -> nextState--;
        }
        int[] actionsFromState = possibleActionsFromState(nextState);
        //the learning rate and eagerness will keep the W value above the lowest reward
        double maxValue = -10;
        for (int nextAction : actionsFromState) {
            double value = Qtable[nextState][nextAction];

            if (value > maxValue)
                maxValue = value;
        }
        return maxValue;
    }

    void printPolicy() {
        System.out.println("\nPrint policy");
        for (int i = 0; i < statesCount; i++) {
            System.out.println("From state " + i + " goto state " + getPolicyFromState(i));
        }
    }

    int getPolicyFromState(int state) {
        int[] actionsFromState = possibleActionsFromState(state);

        double maxValue = Double.MIN_VALUE;
        int policyGotoState = state;

        // Pick to move to the state that has the maximum Q value
        for (int nextAction : actionsFromState) {
            double value = Qtable[state][nextAction];

            if (value > maxValue) {
                maxValue = value;
                policyGotoState = nextAction;
            }
        }
        return policyGotoState;
    }

    void printQ() {
        System.out.println("Q matrix");
        for (int i = 0; i < Qtable.length; i++) {
            System.out.print("From state " + i + ":  ");
            for (int j = 0; j < Qtable[i].length; j++) {
                System.out.printf("%6.2f ", (Qtable[i][j]));
            }
            System.out.println();
        }
    }

    private int TDL() {
        for (int i = 0; i < totalRuns; i++) {
            PlayMap copyMap = map.clone();
            QTDLNode currentState = new QTDLNode(copyMap);
            Controller randomAgent = new RandomController(copyMap, copyMap.getHero());
            int actions = 0;
            while (actions < maxActions) {
                actions++;
                // get old coordinates of the hero
                Point2D oldHeroPosition = copyMap.getHero().getPosition();
                int oldXPos = (int) oldHeroPosition.x;
                int oldYPos = (int) oldHeroPosition.y;

                // chose a random action to do
                int randomAction = randomAgent.getNextAction();
                copyMap.updateGame(randomAction);

                // create new state
                QTDLNode newState = new QTDLNode(copyMap);
                newState.parentState = currentState;
                newState.parentMove = randomAction;
                // get new coordinates of the hero
                Point2D heroPosition = copyMap.getHero().getPosition();
                int xPos = (int) heroPosition.x;
                int yPos = (int) heroPosition.y;
                if (copyMap.isExit(xPos, yPos)) {
                    newState.reward = 1;
                    valueEstimates[xPos][yPos] = 1;
                } else {
                    newState.reward = 0;
                }
                valueEstimates[oldXPos][oldYPos] += alpha * (newState.reward +
                        gamma * valueEstimates[xPos][yPos] - valueEstimates[oldXPos][oldYPos]);
            }
        }

        return 0;
    }

    private int BestActionFromValueEstimates() {
        float nextBestMoveValue = -1;
        int nextBestMoveAction = 0;
        Point2D heroCoords = map.getHero().getPosition();
        int heroXPos = (int) heroCoords.x;
        int heroYPos = (int) heroCoords.y;
        return 0;
    }

    private void initNonTerminalStatesToZero() {
        valueEstimates = new float[map.getMapSizeX()][map.getMapSizeY()];
        for (int i = 0; i < map.getMapSizeX(); i++) {
            for (int j = 0; j < map.getMapSizeY(); j++) {
                valueEstimates[i][j] = 0;
            }
        }
    }


}
