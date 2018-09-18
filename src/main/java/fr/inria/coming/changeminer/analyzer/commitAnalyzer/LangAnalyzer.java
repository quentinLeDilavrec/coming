package fr.inria.coming.changeminer.analyzer.commitAnalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.inria.coming.core.implementation.CommitGit;
import fr.inria.coming.core.implementation.RepositoryPGit;
import fr.inria.coming.core.interfaces.Commit;
import fr.inria.coming.core.interfaces.CommitAnalyzer;
import fr.inria.coming.core.interfaces.RepositoryP;
import fr.inria.coming.main.ConfigurationProperties;

/**
 * 
 * @author Matias Martinez
 *
 */
public class LangAnalyzer implements CommitAnalyzer {

	/**
	 * 
	 */
	private int commitWindows = 1;

	protected Logger log = Logger.getLogger(LangAnalyzer.class.getName());

	private String output = ConfigurationProperties.getProperty("temporal_directory");
	private String prefix = "v";
	private String cloc_path = ConfigurationProperties.getProperty("path_to_cloc");

	protected List<CommitInfo> commitsProcessed = new ArrayList<>();

	public LangAnalyzer(int commitWindows) {
		super();
		this.commitWindows = commitWindows;
	}

	public LangAnalyzer() {
		super();
	}

	@SuppressWarnings("rawtypes")
	public List<CommitInfo> navigateRepo(String repositoryPath, String masterBranch) {

		RepositoryP repo = new RepositoryPGit(repositoryPath, masterBranch);
		this.commitsProcessed.clear();

		// For each commit of a repository
		List<Commit> history = repo.history();
		int i = 0;

		for (Commit c : history) {

			if (i % this.commitWindows == 0) {
				log.debug("Analyzing commit " + i + " out of " + history.size());
				this.analyze(c);
			}
			i++;
		}

		log.info("\n commits analyzed " + i);
		return this.commitsProcessed;
	}

	@Override
	public Object analyze(Commit commit) {

		CommitGit c = (CommitGit) commit;
		String repositoryPath = c.getRepository().getRepository().getDirectory().getAbsolutePath();
		log.debug("Commit ->:  " + c.getName());
		try {
			runCommand(repositoryPath, "git reset --hard master".split(" "));
			File diro = new File(output + prefix + c.getName());
			diro.mkdirs();
			runCommand(repositoryPath,
					("git --work-tree=" + diro.getAbsolutePath() + " checkout " + c.getName() + " .").split(" "));

			List<String> ls = runCommand(repositoryPath, new String[] { cloc_path, diro.getAbsolutePath() });
			Map<String, Integer[]> langcommit = getLanguages(ls);
			this.commitsProcessed.add(new CommitInfo(c.getName(), langcommit, this.commitsProcessed.size()));
			FileUtils.deleteDirectory(diro);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return commitsProcessed;
	}

	private List<String> runCommand(String repositoryPath, String[] command) throws Exception {
		Process p = null;
		ProcessBuilder pb = new ProcessBuilder();

		pb.directory(new File(repositoryPath));
		pb.command(command);
		p = pb.start();

		// p.waitFor(2, TimeUnit.SECONDS);
		p.waitFor();
		java.util.List<String> ls = readOutput(p);

		p.destroy();
		return ls;

	}

	public static List<String> readOutput(Process p) throws IOException {
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

		List<String> lines = new ArrayList<>();
		String s = null;
		while ((s = stdInput.readLine()) != null) {

			lines.add(s);
		}

		stdInput.close();

		return lines;
	}

	public static List<String> readError(Process p) throws IOException {

		BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		List<String> lines = new ArrayList<>();
		String s = null;
		while ((s = stdError.readLine()) != null) {

			lines.add(s);
		}

		stdError.close();

		return lines;
	}

	// files blank comment code
	public Map<String, Integer[]> getLanguages(List<String> lines) {
		Map<String, Integer[]> stats = new HashMap<>();
		boolean activate = false;
		for (String l : lines) {
			if (!activate && l.startsWith("Language")) {
				activate = true;
				// we activate and go the next line
				continue;
			} else {
				if (activate && l.startsWith("SUM")) {
					activate = false;
					continue;
				}
			}
			if (!activate || l.startsWith("--"))
				continue;
			String[] aline = l.split(" ");
			// FORMAT: Language files blank comment code
			Integer[] result = new Integer[4];
			int i = 0;
			String name = "";
			for (String a : aline) {
				if (a.trim().isEmpty())
					continue;
				if (i == 0) {
					name = a;
					i++;
				} else {

					try {
						int statistic = Integer.parseInt(a);
						result[i - 1] = statistic;
						i++;
					} catch (NumberFormatException e) {
						// nothing
					}
				}
			}
			stats.put(name, result);
		}
		return stats;
	}

	public class CommitInfo {
		String commitid;
		int nrCommit = 0;
		Map<String, Integer[]> stats;

		public CommitInfo(String commitid, Map<String, Integer[]> stats) {
			super();
			this.commitid = commitid;
			this.stats = stats;
		}

		public CommitInfo(String commitid, Map<String, Integer[]> stats, int nr) {
			super();
			this.commitid = commitid;
			this.stats = stats;
			this.nrCommit = nr;
		}

		public String getCommitid() {
			return commitid;
		}

		public void setCommitid(String commitid) {
			this.commitid = commitid;
		}

		public Map<String, Integer[]> getStats() {
			return stats;
		}

		public void setStats(Map<String, Integer[]> stats) {
			this.stats = stats;
		}

		@Override
		public String toString() {

			String s = "ci->" + commitid + ":\n";
			for (String lang : this.stats.keySet()) {
				s += "--" + lang + ":\n";
				s += " code:" + this.stats.get(lang)[3] + "\n";// make an enum
			}
			return s;
		}

		@SuppressWarnings("unchecked")
		public JSONObject toJSON() {

			JSONObject root = new JSONObject();
			root.put("commitid", this.commitid);
			root.put("numbercommit", this.nrCommit);
			JSONArray languages = new JSONArray();
			root.put("languages", languages);

			for (String lang : this.stats.keySet()) {
				JSONObject language = new JSONObject();
				languages.add(language);
				language.put("langname", lang);
				// files blank comment code
				language.put("files", this.stats.get(lang)[0]);
				language.put("blank", this.stats.get(lang)[1]);
				language.put("comment", this.stats.get(lang)[2]);
				language.put("code", this.stats.get(lang)[3]);

			}
			return root;
		}

		public int getNrCommit() {
			return nrCommit;
		}

		public void setNrCommit(int nrCommit) {
			this.nrCommit = nrCommit;
		}

	}

	public List<CommitInfo> getCommitsProcessed() {
		return commitsProcessed;
	}

	public void setCommitsProcessed(List<CommitInfo> commitsProcessed) {
		this.commitsProcessed = commitsProcessed;
	}

	@SuppressWarnings("unchecked")
	public JSONObject resultToJSON() {

		JSONObject root = new JSONObject();
		JSONArray commits = new JSONArray();
		root.put("commits", commits);

		for (CommitInfo commitInfo : commitsProcessed) {
			commits.add(commitInfo.toJSON());
		}
		return root;
	}

}