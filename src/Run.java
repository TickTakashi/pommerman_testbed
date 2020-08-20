import core.Game;
import players.*;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.rhea.RHEAPlayer;
import players.rhea.utils.Constants;
import players.rhea.utils.RHEAParams;
import utils.*;

import java.util.*;

import static utils.Types.VISUALS;

public class Run {

    private static void printHelp()
    {
        System.out.println("Usage: java Run [args]");
        System.out.println("\t [arg index = 0] Game Mode. 0: FFA; 1: TEAM");
        System.out.println("\t [arg index = 1] Number of level generation seeds. \"-1\" to execute with the ones from paper (20).");
        System.out.println("\t [arg index = 2] Repetitions per seed [N]. \"1\" for one game only with visuals.");
        System.out.println("\t [arg index = 3] Vision Range [VR]. (0, 1, 2 for PO; -1 for Full Observability)");
        System.out.println("\t [arg index = 4-7] Agents. When in TEAM, agents are mates as indices 4-6, 5-7:");
        System.out.println("\t\t 0 DoNothing");
        System.out.println("\t\t 1 Random");
        System.out.println("\t\t 2 OSLA");
        System.out.println("\t\t 3 SimplePlayer");
        System.out.println("\t\t 4 RHEA 200 itereations, shift buffer, pop size 1, random init, length: 12");
        System.out.println("\t\t 5 MCTS 200 iterations, length: 12");
    }

    public static long GenerateRuleset(){
        // TODO: Sets game parameters, the  Returns the seed value used to generate the ruleset
        Random rand = new Random();
        long seed = (long)(Math.random() * Long.MAX_VALUE);
        rand.setSeed(seed);

        Types.BOMB_LIFE = 5 + rand.nextInt(15);
        Types.FLAME_LIFE = 2 + rand.nextInt(8);
        Types.DEFAULT_BOMB_BLAST = 1 + rand.nextInt(3);
        Types.DEFAULT_BOMB_AMMO = 1 + rand.nextInt(3);
        Types.BOARD_NUM_RIGID = 5 + rand.nextInt(25);
        Types.BOARD_NUM_WOOD = 5 + rand.nextInt(25);
        Types.BOARD_NUM_ITEMS = 0 + rand.nextInt(Types.BOARD_NUM_WOOD);

        return seed;
    }

    public static void PrintRuleset(long seed){
        Random rand = new Random();
        rand.setSeed(seed);
        System.out.println("For Seed " + seed + ", Generated Rules are: ");
        System.out.println("Types.BOMB_LIFE = " + (5 + rand.nextInt(15)));
        System.out.println("Types.FLAME_LIFE = " + (2 + rand.nextInt(8)));
        System.out.println("Types.DEFAULT_BOMB_BLAST = " + (1 + rand.nextInt(3)));
        System.out.println("Types.DEFAULT_BOMB_AMMO = " + (1 + rand.nextInt(3)));
        System.out.println("Types.BOARD_NUM_RIGID = " + (5 + rand.nextInt(25)));
        System.out.println("Types.BOARD_NUM_WOOD = " + (5 + rand.nextInt(25)));
        System.out.println("Types.BOARD_NUM_ITEMS = " + (0 + rand.nextInt(Types.BOARD_NUM_WOOD)));
    }

    public static void main(String[] args) {
        //default
        if(args.length == 0)
            args = new String[]{"0", "1", "1", "-1", "0", "1", "2", "3"};

        if(args.length != 8) {
            printHelp();
            return;
        }

        int gamesPerTrial = 50;
        int totalTrials = 30;

        // TODO: Change the actual args passed in instead.
        args[2] = "" + gamesPerTrial;

        float highestFitness = -1;
        long bestSeed = 0;

        Hashtable<Long, Float> fitDict = new Hashtable<>();

        // Step 5:
        // - Repeat 1-4 M times.
        for (int i = 0; i < totalTrials; i++) {
            // Step 1:
            // - Generate Ruleset
            long ruleSet = GenerateRuleset();

            // Step 2:
            // - Run N Games
            double[] results = run(args);

            // Step 3:
            // - Evaluate Fitness of Ruleset
            float fitness = fitness(results);

            // Step 4:
            // - Add Results to Collection
            fitDict.put(ruleSet, fitness);

            if (fitness > highestFitness){
                highestFitness = fitness;
                bestSeed = ruleSet;
                System.out.println("Found a new best seed: " + bestSeed);
            }
        }

        // Step 6:
        // - Select Highest Performing Ruleset from Collection
        System.out.println("best seed was " + bestSeed);

        PrintRuleset(bestSeed);
    }

    public static float fitness(double[] agentWinrates){
        // TODO: Factor in Ticks?
        float fit = 0;
        if (agentWinrates[3] >  agentWinrates[2]){
            fit += 0.25f;
        }

        if (agentWinrates[2] >  agentWinrates[1]){
            fit += 0.25f;
        }

        if (agentWinrates[1] >  agentWinrates[0]){
            fit += 0.25f;
        }

        fit += (agentWinrates[3] - agentWinrates[0]) * 0.25f;


        return fit;
    }

    public static double[] run(String[] args) {
//        long start_time = System.currentTimeMillis();
//        System.out.println("Delta " + (System.currentTimeMillis() - start_time));
        try {

            Random rnd = new Random();

            // Create players
            ArrayList<Player> players = new ArrayList<>();
            int playerID = Types.TILETYPE.AGENT0.getKey();
            int boardSize = Types.BOARD_SIZE;

            Types.GAME_MODE gMode = Types.GAME_MODE.FFA;
            if(Integer.parseInt(args[0]) == 1)
                gMode = Types.GAME_MODE.TEAM;

            int S = Integer.parseInt(args[1]);
            int N = Integer.parseInt(args[2]);
            Types.DEFAULT_VISION_RANGE = Integer.parseInt(args[3]);

            long seeds[];

            if (S == -1)
            {
                //Special case, these seeds are fixed for the experiments in the paper:
                seeds = new long[] {93988, 19067, 64416, 83884, 55636, 27599, 44350, 87872, 40815,
                        11772, 58367, 17546, 75375, 75772, 58237, 30464, 27180, 23643, 67054, 19508};
            }else
            {
                if(S <= 0)
                    S = 1;

                //Otherwise, all seeds are random
                seeds = new long[S];
                for(int i = 0; i < S; i++)
                    seeds[i] = rnd.nextInt(100000);
            }

            long seed = 0;

            String[] playerStr = new String[4];

            for(int i = 4; i <= 7; ++i) {
                int agentType = Integer.parseInt(args[i]);
                Player p = null;


                switch(agentType) {
                    case 0:
                        RHEAParams rheaParams0 = new RHEAParams();
                        rheaParams0.budget_type = Constants.ITERATION_BUDGET;
                        rheaParams0.iteration_budget = 100;
                        rheaParams0.individual_length = 4;
                        rheaParams0.heurisic_type = Constants.CUSTOM_HEURISTIC;

                        p = new RHEAPlayer(seed, playerID++, rheaParams0);
                        playerStr[i-4] = "RHEA";
                        break;
                    case 1:
                        RHEAParams rheaParams1 = new RHEAParams();
                        rheaParams1.budget_type = Constants.ITERATION_BUDGET;
                        rheaParams1.iteration_budget = 200;
                        rheaParams1.individual_length = 8;
                        rheaParams1.heurisic_type = Constants.CUSTOM_HEURISTIC;

                        p = new RHEAPlayer(seed, playerID++, rheaParams1);
                        playerStr[i-4] = "RHEA";
                        break;
                    case 2:
                        RHEAParams rheaParams2 = new RHEAParams();
                        rheaParams2.budget_type = Constants.ITERATION_BUDGET;
                        rheaParams2.iteration_budget = 400;
                        rheaParams2.individual_length = 12;
                        rheaParams2.heurisic_type = Constants.CUSTOM_HEURISTIC;

                        p = new RHEAPlayer(seed, playerID++, rheaParams2);
                        playerStr[i-4] = "RHEA";
                        break;

                    case 3:
                        RHEAParams rheaParams3 = new RHEAParams();
                        rheaParams3.budget_type = Constants.ITERATION_BUDGET;
                        rheaParams3.iteration_budget = 500;
                        rheaParams3.individual_length = 16;
                        rheaParams3.heurisic_type = Constants.CUSTOM_HEURISTIC;

                        p = new RHEAPlayer(seed, playerID++, rheaParams3);
                        playerStr[i-4] = "RHEA";
                        break;

                    default:
                        System.out.println("WARNING: Invalid agent ID: " + agentType );
                }

                players.add(p);
            }

            String gameIdStr = "";
            for(int i = 0; i <= 7; ++i) {
                gameIdStr += args[i];
                if(i != 7)
                    gameIdStr+="-";
            }

            Game game = new Game(seeds[0], boardSize, gMode, gameIdStr);

            // Make sure we have exactly NUM_PLAYERS players
            assert players.size() == Types.NUM_PLAYERS;
            game.setPlayers(players);

            System.out.print(gameIdStr + " [");
            for(int i = 0; i < playerStr.length; ++i) {
                System.out.print(playerStr[i]);
                if(i != playerStr.length-1)
                    System.out.print(',');

            }
            System.out.println("]");

            return runGames(game, seeds, N, false);
        } catch(Exception e) {
            e.printStackTrace();
            printHelp();
        }


        return null;
    }

    /**
     * Runs 1 game.
     * @param g - game to run
     * @param ki1 - primary key controller
     * @param ki2 - secondary key controller
     * @param separateThreads - if separate threads should be used for the agents or not.
     */
    public static void runGame(Game g, KeyController ki1, KeyController ki2, boolean separateThreads) {
        WindowInput wi = null;
        GUI frame = null;
        if (VISUALS) {
            frame = new GUI(g, "Java-Pommerman", ki1, false, true);
            wi = new WindowInput();
            wi.windowClosed = false;
            frame.addWindowListener(wi);
            frame.addKeyListener(ki1);
            frame.addKeyListener(ki2);
        }

        g.run(frame, wi, separateThreads);
    }

    public static double[] runGames(Game g, long seeds[], int repetitions, boolean useSeparateThreads){
        int numPlayers = g.getPlayers().size();
        int[] winCount = new int[numPlayers];
        int[] tieCount = new int[numPlayers];
        int[] lossCount = new int[numPlayers];

        int[] overtimeCount = new int[numPlayers];

        int numSeeds = seeds.length;
        int totalNgames = numSeeds * repetitions;

        for(int s = 0; s<numSeeds; s++) {
            long seed = seeds[s];

            for (int i = 0; i < repetitions; i++) {
                long playerSeed = System.currentTimeMillis();

                System.out.print( playerSeed + ", " + seed + ", " + (s*repetitions + i) + "/" + totalNgames + ", ");

                g.reset(seed);
                EventsStatistics.REP = i;
                GameLog.REP = i;

                // Set random seed for players and reset them
                ArrayList<Player> players = g.getPlayers();
                for (int p = 0; p < g.nPlayers(); p++) {
                    players.get(p).reset(playerSeed, p);
                }
                Types.RESULT[] results = g.run(useSeparateThreads);

                for (int pIdx = 0; pIdx < numPlayers; pIdx++) {
                    switch (results[pIdx]) {
                        case WIN:
                            winCount[pIdx]++;
                            break;
                        case TIE:
                            tieCount[pIdx]++;
                            break;
                        case LOSS:
                            lossCount[pIdx]++;
                            break;
                    }
                }

                int[] overtimes = g.getPlayerOvertimes();
                for(int j = 0; j < overtimes.length; ++j)
                    overtimeCount[j] += overtimes[j];

            }
        }

        double[] winPercentagesPerAgent = new double[numPlayers];

        //Done, show stats
        System.out.println("N \tWin \tTie \tLoss \tPlayer (overtime average)");
        for (int pIdx = 0; pIdx < numPlayers; pIdx++) {
            String player = g.getPlayers().get(pIdx).getClass().toString().replaceFirst("class ", "");

            double winPerc = winCount[pIdx] * 100.0 / (double)totalNgames;
            double tiePerc = tieCount[pIdx] * 100.0 / (double)totalNgames;
            double lossPerc = lossCount[pIdx] * 100.0 / (double)totalNgames;
            double overtimesAvg = overtimeCount[pIdx] / (double)totalNgames;

            winPercentagesPerAgent[pIdx] = winPerc;

            System.out.println(totalNgames + "\t" + winPerc + "%\t" + tiePerc + "%\t" + lossPerc + "%\t" + player + " (" + overtimesAvg + ")" );
        }

        return winPercentagesPerAgent;
    }

    private class Results
    {
        public double[] winrates;
        public int num = 0;

        public Results(double[] winrates){
            this.winrates = winrates;
            num = 1;
        }

        public void add(Results other){
            for(int i = 0; i < winrates.length; i++){
                winrates[i] = (winrates[i] * num + other.winrates[i]) / (num + 1);
            }

            num = num + 1;
        }
    }
}


