package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.Dao;
import com.xmonit.solar.arduino.dao.DomainDao;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

abstract public class DomainDaoController<DataT, DaoT extends DomainDao> extends DaoController<DataT, DaoT> {

	// TODO - add annotation to all field accessors and group them on startup
	@GetMapping(value = "fieldAccessors", produces = "application/json")
	public List<Dao.FieldMetaData> fieldAccessors() throws ArduinoException {
		return createDao(null).getFields();
	}

	@GetMapping(value = "flushItemCache", produces = "application/json")
	@CacheEvict(cacheNames = "domainItem", allEntries = true)
	public void flushItemCache() throws ArduinoException {

	}

	@GetMapping(value = "flushListCache", produces = "application/json")
	@CacheEvict(cacheNames = "domainList", allEntries = true)
	public void flushListCache() throws ArduinoException {

	}

	@GetMapping(value = "get/{arduinoName}/{id}", produces = "application/json")
	@Cacheable(cacheNames = "domainItem", key = "{#root.targetClass, #arduinoName, #id}")
	public DataT getById(@PathVariable String arduinoName, @PathVariable Integer id,
			@RequestParam(required = false, defaultValue = "true") boolean cached) throws ArduinoException {
		return createDao(getBus(arduinoName)).get(id);
	}

	@GetMapping(value = "list/{arduinoName}", produces = "application/json")
	@Cacheable(cacheNames = "domainList", key = "{#root.targetClass, #arduinoName}")
	public DataT[] list(@PathVariable String arduinoName) throws ArduinoException {
		return createDao(getBus(arduinoName)).list();
	}

	@GetMapping(value = { "list", "" }, produces = "application/json")
	@Cacheable(cacheNames = "domainList", key = "#root.targetClass")
	public List<TaskResult<DataT[]>> listAll() throws ArduinoException {
		AsyncTask<DataT[]> task = (bus) -> {
			return new TaskResult<DataT[]>(bus.name, bus.id, list(bus.name));
		};
		return process(getBuses(), task);
	}

}