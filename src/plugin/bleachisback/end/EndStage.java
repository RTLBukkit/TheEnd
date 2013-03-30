package plugin.bleachisback.end;

import java.util.HashMap;

public enum EndStage 
{
	BEFORE("before"),
	DURING_ONE("during1"),
	DURING_TWO("during2"),
	COMPLETED("done");
	
	private String name;
	private final static HashMap<String, EndStage> FROM_NAME= new HashMap<String, EndStage>();
	
	EndStage(String name)
	{
		this.name=name;
	}
	
	String getName()
	{
		return name;
	}
	
	public static EndStage fromName(String string)
	{
		return FROM_NAME.containsKey(string)?FROM_NAME.get(string):BEFORE;
	}
	
    static 
    {
        for (EndStage stage : values()) 
        {
        	FROM_NAME.put(stage.getName(), stage);
        }
    }
}
