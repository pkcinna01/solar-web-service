package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.SingletonDao;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

abstract public class SingletonDaoController<DataT, DaoT extends SingletonDao<DataT>>
		extends DaoController<DataT, DaoT> {

	// this will flush env, eeprom, and any other singletons (cannot specify a
	// specific singleton)
	@GetMapping(value = "flush", produces = "application/json")
	@CacheEvict(cacheNames = "singleton", allEntries = true)
	public void flush() throws ArduinoException {

	}

	@GetMapping(value = "get/{name}", produces = "application/json")
	@Cacheable(cacheNames = "singleton", key = "{#root.targetClass, #name}")
	public DataT get(@PathVariable String name) throws ArduinoException {
		return createDao(getBus(name)).get();
	}

	@GetMapping(value = { "list", "" }, produces = "application/json")
	@Cacheable(cacheNames = { "singleton" }, key = "#root.targetClass")
	public List<TaskResult<DataT>> getAll() throws ArduinoException {
		AsyncTask<DataT> task = (bus) -> {
			return new TaskResult<DataT>(bus.name, bus.id, get(bus.name));
		};
		return process(getBuses(), task);
	}

}