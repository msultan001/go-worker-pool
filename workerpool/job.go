package workerpool



type Job struct {
	ID    int
	Input int
}

var (
	Jobs = []Job{
		{ID: 1, Input: 10},
		{ID: 2, Input: 20},
		{ID: 3, Input: 30},
		{ID: 4, Input: 40},
		{ID: 5, Input: 50},
	}
)