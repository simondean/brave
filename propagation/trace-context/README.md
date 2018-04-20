# brave-propagation-trace-context

TODO: summarize impl, for example that we use b3 to handle local state handling, likely with the
the state name `b3` until/unless we have a handler otherwise. Also, mention how we operate in
different ways, for example complete pass-through if supported, or note why not.

TODO: add RATIONALE.md for decisions made like implementation choices and why they were made due to
Brave or how the tracecontext format was written or changed.

TODO: this is very early impl and doesn't do things like compare if it
should really resume a trace or not. It is a several hour spike and will
continue
