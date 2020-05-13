package wooteco.subway.admin.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedMultigraph;
import org.springframework.stereotype.Service;

import wooteco.subway.admin.domain.Line;
import wooteco.subway.admin.domain.LineStation;
import wooteco.subway.admin.domain.Station;
import wooteco.subway.admin.dto.LineDetailResponse;
import wooteco.subway.admin.dto.LineRequest;
import wooteco.subway.admin.dto.LineStationCreateRequest;
import wooteco.subway.admin.dto.PathResponse;
import wooteco.subway.admin.dto.StationResponse;
import wooteco.subway.admin.dto.WholeSubwayResponse;
import wooteco.subway.admin.repository.LineRepository;
import wooteco.subway.admin.repository.StationRepository;

@Service
public class LineService {
	private LineRepository lineRepository;
	private StationRepository stationRepository;

	public LineService(LineRepository lineRepository, StationRepository stationRepository) {
		this.lineRepository = lineRepository;
		this.stationRepository = stationRepository;
	}

	public Line save(Line line) {
		return lineRepository.save(line);
	}

	public List<Line> showLines() {
		return lineRepository.findAll();
	}

	public void updateLine(Long id, LineRequest request) {
		Line persistLine = lineRepository.findById(id).orElseThrow(RuntimeException::new);
		persistLine.update(request.toLine());
		lineRepository.save(persistLine);
	}

	public void deleteLineById(Long id) {
		lineRepository.deleteById(id);
	}

	public void addLineStation(Long id, LineStationCreateRequest request) {
		Line line = lineRepository.findById(id).orElseThrow(RuntimeException::new);
		LineStation lineStation = new LineStation(request.getPreStationId(), request.getStationId(),
			request.getDistance(), request.getDuration());
		line.addLineStation(lineStation);

		lineRepository.save(line);
	}

	public void removeLineStation(Long lineId, Long stationId) {
		Line line = lineRepository.findById(lineId).orElseThrow(RuntimeException::new);
		line.removeLineStationById(stationId);
		lineRepository.save(line);
	}

	public LineDetailResponse findLineWithStationsById(Long id) {
		Line persistLine = lineRepository.findById(id).orElseThrow(RuntimeException::new);
		List<Station> stations = persistLine.getLineStationsId()
			.stream()
			.map(stationId -> stationRepository.findById(stationId))
			.map(station -> station.orElseThrow(NoSuchElementException::new))
			.collect(Collectors.toList());

		return LineDetailResponse.of(persistLine, StationResponse.listOf(stations));
	}

	public WholeSubwayResponse wholeLines() {
		List<Line> lines = lineRepository.findAll();

		return lines.stream()
			.map(line -> findLineWithStationsById(line.getId()))
			.collect(Collectors.collectingAndThen(Collectors.toList(), WholeSubwayResponse::of));
	}

	public PathResponse findPath(String departStationName, String arrivalStationName) {
		WeightedMultigraph<Long, DefaultWeightedEdge> graph
			= new WeightedMultigraph<>(DefaultWeightedEdge.class);
		Station departStation = stationRepository.findByName(departStationName)
			.orElseThrow(NoSuchElementException::new);
		Station arrivalStation = stationRepository.findByName(arrivalStationName)
			.orElseThrow(NoSuchElementException::new);
		List<Station> stations = stationRepository.findAll();
		stations.forEach(station -> graph.addVertex(station.getId()));
		List<Line> lines = lineRepository.findAll();
		lines.stream()
			.flatMap(line -> line.getStations().stream())
			.forEach(lineStation -> setDistanceGraph(graph, lineStation));

		DijkstraShortestPath<Long, DefaultWeightedEdge> dijkstraShortestPath = new DijkstraShortestPath<>(graph);
		GraphPath<Long, DefaultWeightedEdge> path = dijkstraShortestPath.getPath(departStation.getId(),
			arrivalStation.getId());
		List<Long> shortestPath = path.getVertexList();

		List<StationResponse> stationResponses = shortestPath.stream()
			.map(id -> stationRepository.findById(id).orElseThrow(NoSuchElementException::new))
			.map(StationResponse::of)
			.collect(Collectors.toList());

		int duration = IntStream.range(0, shortestPath.size() - 1)
			.map(index -> findPathLineStationDuration(lines, shortestPath, index))
			.sum();

		return PathResponse.of(stationResponses, (int)path.getWeight(), duration);
	}

	private int findPathLineStationDuration(List<Line> lines, List<Long> shortestPath, int index) {
		return lines.stream()
			.flatMap(line -> line.getStations().stream())
			.filter(lineStation -> isPathLineStation(lineStation, shortestPath, index))
			.mapToInt(LineStation::getDuration)
			.sum();
	}

	private void setDistanceGraph(WeightedMultigraph<Long, DefaultWeightedEdge> graph, LineStation lineStation) {
		Long preStationId = lineStation.getPreStationId();
		Long stationId = lineStation.getStationId();
		int distance = lineStation.getDistance();

		if (Objects.nonNull(preStationId)) {
			graph.setEdgeWeight(graph.addEdge(preStationId, stationId), distance);
		}
	}

	private boolean isPathLineStation(LineStation lineStation, List<Long> shortestPath, int index) {
		if (Objects.isNull(lineStation.getPreStationId())) {
			return false;
		}
		return lineStation.getPreStationId().equals(shortestPath.get(index))
			&& lineStation.getStationId().equals(shortestPath.get((index + 1)));
	}

	private void setDurationGraph(WeightedMultigraph<Long, DefaultWeightedEdge> graph, LineStation lineStation) {
		Long preStationId = lineStation.getPreStationId();
		Long stationId = lineStation.getStationId();
		int distance = lineStation.getDistance();

		if (Objects.nonNull(preStationId)) {
			graph.setEdgeWeight(graph.addEdge(preStationId, stationId), distance);
		}
	}
}
