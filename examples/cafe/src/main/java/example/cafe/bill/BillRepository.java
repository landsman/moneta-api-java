package example.cafe.bill;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

interface BillRepository extends ListCrudRepository<Bill, Long> {

    List<Bill> findByMonetaEntryReferenceIsNullOrderById();

    List<Bill> findAllByOrderByIdDesc();
}
