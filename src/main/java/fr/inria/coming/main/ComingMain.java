package fr.inria.coming.main;

import java.util.List;
import java.util.Map;

import fr.inria.coming.changeminer.analyzer.RepositoryInspector;
import fr.inria.coming.changeminer.analyzer.commitAnalyzer.FineGrainChangeCommitAnalyzer;
import fr.inria.coming.changeminer.analyzer.commitAnalyzer.filters.SimpleChangeFilter;
import fr.inria.coming.changeminer.entity.ActionType;
import fr.inria.coming.changeminer.util.ConsoleOutput;
import fr.inria.coming.changeminer.util.XMLOutput;
import fr.inria.coming.core.Parameters;
import fr.inria.coming.core.filter.DummyFilter;
import fr.inria.coming.core.interfaces.Commit;
import gumtree.spoon.diff.operations.Operation;

/**
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 *
 */
public class ComingMain {

	public static void main(String[] args) {

		String message = "USAGE --parameters-- [projectLocation] [entity] [action] [message (optional)] ; to get the entities use -e, to get the actions use -a";

		if (args == null) {
			System.out.println(message);
			return;
		}

		if (args.length < 3)
			return;

		Parameters.setUpProperties();
		Parameters.printParameters();

		RepositoryInspector c = new RepositoryInspector();
		String projectLocation = args[0];
		String entity = args[1];
		String action = args[2];
		String messageHeuristic = (args.length == 4) ? args[3] : "";

		FineGrainChangeCommitAnalyzer analyzer = new FineGrainChangeCommitAnalyzer(
				new SimpleChangeFilter(entity, ActionType.valueOf(action)));

		Map<Commit, List<Operation>> instancesFound = c.analize(projectLocation, "master", analyzer, new DummyFilter());
		ConsoleOutput.printResultDetails(instancesFound);
		XMLOutput.print(instancesFound);
	}

}