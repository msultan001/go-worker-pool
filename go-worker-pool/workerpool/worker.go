package workerpool

import (
	"context"
	"log"
	"time"
)

type Worker struct {
	ID            int
	Jobs          <-chan Job
	ctx           context.Context
	maxRetries    int
	resultsChannl chan Result
}

func NewWorker(
	id int,
	jobs <-chan Job,
	cx context.Context,
	maxTry int,
	resuls chan Result,
) *Worker {
	return &Worker{
		ID:            id,
		Jobs:          jobs,
		ctx:           cx,
		maxRetries:    maxTry,
		resultsChannl: resuls,
	}
}

func (w *Worker) Process(
	j Job,
) Result {
	var result Result
	select {
	case <-w.ctx.Done():

	case <-time.After(time.Duration(1 * time.Millisecond)):

		result.Output = j.Input * 2
		result.JobID = j.ID
		result.Err = nil
	}
	return result
}

func (w *Worker) Listen() error {
	for {
		select {
		case job, ok := <-w.Jobs:
			if !ok {
				// log.Println("jobs channel is closed, worker is exiting")
				return nil
			}
			result := w.ProcessWithRetry(job)
			w.resultsChannl <- result
			if result.Err != nil {
				log.Printf("Worker %d failed to process job %d: %v\n", w.ID, job.ID, result.Err)
				return result.Err
			}
		case <-w.ctx.Done():
			return w.ctx.Err()
		}
	}
}

func (w *Worker) ProcessWithRetry(job Job) Result {
	var result Result

	for attempt := 0; attempt <= w.maxRetries; attempt++ {


		if w.ctx.Err() != nil {
			result.Err = w.ctx.Err()
			return result
		}

		
		result = w.Process(job)
		
		if result.Err == nil {
			return result
		}

		
		if attempt == w.maxRetries {
			return result
		}

		delay := time.Duration(1<<attempt) * time.Second

		
		timer := time.NewTimer(delay)

		select {
		case <-timer.C:

		case <-w.ctx.Done():
			if !timer.Stop() {
				select {
				case <-timer.C:
				default:
				}
			}

			result.Err = w.ctx.Err()
			return result
		}
	}

	return result
}
